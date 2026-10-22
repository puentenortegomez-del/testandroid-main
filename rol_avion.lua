-- SCRIPT AEREO GENERICO AUTONOMO.
-- Los roles concretos (piloto_combate.lua, bombardero.lua y
-- transportador_cargas.lua) ya no dependen de este archivo; este queda como
-- alternativa manual ejecutable con el rol como argumento: combat|bomber|transport

local role = tostring((...))
local labels = {
    combat = "PILOTO DE COMBATE",
    bomber = "BOMBARDERO",
    transport = "TRANSPORTADOR DE CARGAS",
}
if not labels[role] then role = "combat" end

local REFUEL_AT = 20
local REFUEL_RETRY = 15
local AUTOPILOT_RETRY = 10
local STASH_SIZE = 9
local OWNERS_FILE = "owners.cfg"

local function failed(value)
    return type(value) == "table" and value[1] == true
end

local function hand()
    local ok, name, count = pcall(android.getHandInfo, "main")
    if not ok or type(name) ~= "string" then return nil, 0 end
    local lower = name:lower()
    if lower == "" or lower == "air" or lower == "minecraft:air" then
        return nil, 0
    end
    return name, tonumber(count) or 0
end

local function isFuel(name)
    if type(name) ~= "string" then return false end
    local lower = name:lower()
    if lower:find("redstone_torch", 1, true)
        or lower:find("redstone torch", 1, true) then
        return false
    end
    return lower:find("redstone", 1, true) ~= nil
end

local function isEmpty(name)
    if type(name) ~= "string" then return true end
    local lower = name:lower()
    return lower == "" or lower == "air"
        or lower == "minecraft:air" or lower == "empty"
end

local function inventory()
    local items = {}
    for slot = 0, STASH_SIZE - 1 do
        local ok, name, extra = pcall(android.getSlotInfo, slot)
        if ok then
            if type(name) == "table" then
                name = name.name or name.id or name.item
            elseif type(name) == "boolean" and type(extra) == "string" then
                name = extra
            end
            items[#items + 1] = {
                slot = slot, name = name, empty = isEmpty(name)
            }
        end
    end
    return items
end

local function refuel()
    local held = hand()
    local holdingFuel = held and isFuel(held)
    local fuelSlot
    for _, item in ipairs(inventory()) do
        if isFuel(item.name) then fuelSlot = item.slot break end
    end
    if not holdingFuel and not fuelSlot then return false end

    local savedSlot
    if not holdingFuel and held then
        for _, item in ipairs(inventory()) do
            if item.empty and item.slot ~= fuelSlot then
                savedSlot = item.slot
                break
            end
        end
        if not savedSlot then return false end
        local ok, result = pcall(android.storeItem, savedSlot)
        if not ok or failed(result) then return false end
    end

    if not holdingFuel then pcall(android.equipSlot, fuelSlot) end
    local before = android.fuelLevel()
    pcall(android.refuel, 1)
    local after = android.fuelLevel()
    if not holdingFuel and hand() then pcall(android.storeItem, fuelSlot) end
    if savedSlot then pcall(android.equipSlot, savedSlot) end
    print("Fuel android: " .. tostring(before) .. " -> " .. tostring(after))
    return after > before
end

local function loadOwners()
    local owners = {}
    if not fs.exists(OWNERS_FILE) then return owners end
    local file = fs.open(OWNERS_FILE, "r")
    if not file then return owners end
    for part in file.readAll():gmatch("[^,\n]+") do
        local name = part:match("^%s*(.-)%s*$")
        if name ~= "" then owners[name:lower()] = true end
    end
    file.close()
    return owners
end

local currentUnit
local function unitId()
    return tostring((currentUnit and (currentUnit.uuid or currentUnit.UUID)) or "")
end

local function requestAutopilot()
    -- AndroidsCompat intercepta este mensaje en el servidor y monta al Android
    -- en el avion vacio mas cercano, asignando tambien el rol.
    pcall(android.sendChatMessage, "!androidscompat autopilot " .. role
        .. " " .. unitId())
end

local function requestOrder(order, target)
    local message = "!androidscompat order " .. unitId() .. " " .. order
    if target and target ~= "" then message = message .. " " .. target end
    pcall(android.sendChatMessage, message)
end

local function main()
    local unit = android.getSelf()
    currentUnit = unit
    local owners = loadOwners()
    local lastFuel = -REFUEL_RETRY
    local lastAutopilot = -AUTOPILOT_RETRY
    local lastStatus = 0

    print(labels[role] .. " activo")
    print("Montaje autonomo: buscando avion vacio cercano")
    print("Rol autonomo: " .. role)
    print("El avion debe tener combustible propio")
    if role == "combat" then
        print("Combate: aviones enemigos y objetivos terrestres")
    elseif role == "bomber" then
        print("Bombardero: objetivos terrestres")
    else
        print("Transporte: no dispara")
    end
    print("Armas, recarga, motor, armadura y mochila: androidscompat")

    parallel.waitForAny(
        function()
            while true do
                local now = os.clock()
                if now - lastAutopilot >= AUTOPILOT_RETRY then
                    requestAutopilot()
                    lastAutopilot = now
                end
                if android.fuelLevel() < REFUEL_AT
                    and now - lastFuel >= REFUEL_RETRY then
                    refuel()
                    lastFuel = now
                end
                if now - lastStatus >= 15 then
                    local self = android.getSelf()
                    print(labels[role] .. " | HP=" .. tostring(self.health)
                        .. " | fuel=" .. tostring(android.fuelLevel()))
                    lastStatus = now
                end
                sleep(1)
            end
        end,
        function()
            while true do
                local event, data = os.pullEvent()
                if event == "key" and data == keys.q then return end
                if event == "onChatMessage" and type(data) == "table"
                    and owners[tostring(data.senderName or ""):lower()] then
                    local target, command, argument = tostring(data.message or "")
                        :match("^!avion%s+(%S+)%s+(%S+)%s*(%S*)")
                    if target and (target:lower() == "all"
                        or target:lower() == tostring(unit.name):lower()) then
                        command = command:lower()
                        if command == "mount" then
                            requestAutopilot()
                        elseif command == "capture" then
                            pcall(android.sendChatMessage, "!androidscompat capture")
                        elseif command == "imprison" then
                            pcall(android.sendChatMessage, "!androidscompat imprison")
                        elseif command == "release" then
                            pcall(android.sendChatMessage, "!androidscompat release")
                        elseif command == "patrol" or command == "return"
                            or command == "hold" or command == "follow"
                            or command == "guard" or command == "rescue"
                            or command == "logistics" then
                            requestOrder(command, argument)
                        elseif command == "status" then
                            pcall(android.sendChatMessage,
                                labels[role] .. " | modo=" .. role
                                .. " | fuel=" .. tostring(android.fuelLevel()))
                        end
                    end
                end
            end
        end
    )
    pcall(android.cancelTask)
    print(labels[role] .. " detenido.")
end

local ok, err = pcall(main)
if not ok then
    pcall(android.cancelTask)
    print("Error: " .. tostring(err))
end
