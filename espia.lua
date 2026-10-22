-- ESPIA AUTONOMO. No depende de rol_avion.lua ni de otro script maestro.
-- El mod androidscompat mantiene el modo encubierto en servidor y genera
-- informes de jugadores, enemigos, Androids y aviones cercanos.

local OWNERS_FILE = "owners.cfg"
local RETRY = 10
local INTEL_RETRY = 15
local STATUS_RETRY = 20

local function loadOwners()
    local result = {}
    if not fs.exists(OWNERS_FILE) then return result end
    local file = fs.open(OWNERS_FILE, "r")
    if not file then return result end
    for name in file.readAll():gmatch("[^,\n]+") do
        name = name:match("^%s*(.-)%s*$")
        if name ~= "" then result[name:lower()] = true end
    end
    file.close()
    return result
end

local function idOf(unit)
    return tostring(unit.uuid or unit.UUID or "")
end

local function send(message)
    pcall(android.sendChatMessage, message)
end

local function activate(unit)
    send("!androidscompat spy on " .. idOf(unit))
end

local function deactivate(unit)
    send("!androidscompat spy off " .. idOf(unit))
end

local function intel(unit)
    send("!androidscompat intel " .. idOf(unit))
end

local function order(unit, command, target)
    send("!androidscompat order " .. idOf(unit) .. " " .. command
        .. (target ~= "" and (" " .. target) or ""))
end

local function main()
    local unit = android.getSelf()
    local owners = loadOwners()
    local nextActivate, nextIntel, nextStatus = -RETRY, -INTEL_RETRY, 0

    print("ESPIA autonomo activo")
    print("Modo encubierto | informes de inteligencia | sin ataque")
    print("Ordenes: !espia all report|hide|show|follow|guard|return|hold")

    parallel.waitForAny(
        function()
            while true do
                local now = os.clock()
                if now >= nextActivate then
                    activate(unit)
                    nextActivate = now + RETRY
                end
                if now >= nextIntel then
                    intel(unit)
                    nextIntel = now + INTEL_RETRY
                end
                if now >= nextStatus then
                    local self = android.getSelf()
                    print("ESPIA | HP=" .. tostring(self.health)
                        .. " | pos=" .. tostring(self.posX) .. ","
                        .. tostring(self.posY) .. "," .. tostring(self.posZ))
                    nextStatus = now + STATUS_RETRY
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
                        :match("^!espia%s+(%S+)%s+(%S+)%s*(%S*)")
                    if target and (target:lower() == "all"
                        or target:lower() == tostring(unit.name):lower()) then
                        command = command:lower()
                        if command == "report" or command == "intel" then
                            intel(unit)
                        elseif command == "hide" or command == "infiltrate"
                            or command == "on" then
                            activate(unit)
                        elseif command == "show" or command == "off" then
                            deactivate(unit)
                        elseif command == "follow" or command == "guard"
                            or command == "patrol" or command == "return"
                            or command == "hold" or command == "rescue"
                            or command == "logistics" then
                            order(unit, command, argument)
                        elseif command == "status" then
                            send("ESPIA activo | intel=automatico | modo=encubierto")
                        end
                    end
                end
            end
        end
    )
    deactivate(unit)
    pcall(android.cancelTask)
    print("ESPIA detenido.")
end

local ok, err = pcall(main)
if not ok then
    deactivate(android.getSelf())
    pcall(android.cancelTask)
    print("Error: " .. tostring(err))
end
