-- Rol Explorador (CC:Androids 1.3.0 / Forge 1.20.1).
-- Patrulla, detecta bloques/hostiles visibles y comunica coordenadas; no ataca ni mina.
-- Funciona de forma autónoma desde la terminal del androide; no requiere el ordenador central.

local SCAN_DELAY = 0.8
local PATROL_RADIUS = 8
local REFUEL_AT = 20
local FUEL_SLOT = 7
local AMMO_SLOTS = { 1, 2, 3 }
local RETREAT_HEALTH = 6
local REPORT_DELAY = 8
local OWNERS_FILE = "owners.cfg"

local function parseOwners(input)
    local owners, names = {}, {}
    for part in tostring(input):gmatch("[^,]+") do
        local name = part:match("^%s*(.-)%s*$")
        if name ~= "" and not owners[name:lower()] then
            owners[name:lower()] = true
            names[#names + 1] = name
        end
    end
    return owners, names
end

-- First run asks once and persists one or more comma-separated player names.
local function loadOwners()
    if fs.exists(OWNERS_FILE) then
        local file = fs.open(OWNERS_FILE, "r")
        if not file then print("No se pudo leer " .. OWNERS_FILE .. ".") return nil end
        local owners, names = parseOwners(file.readAll())
        file.close()
        if #names == 0 then
            print(OWNERS_FILE .. " está vacío o no es válido; no aceptaré comandos remotos.")
            return nil
        end
        print("Propietarios cargados: " .. #names .. ".")
        return owners
    end

    print("Configuración inicial de propietarios (solo esta vez).")
    print("Escribe uno o varios nombres separados por comas:")
    local owners, names = parseOwners(read())
    while #names == 0 do
        print("Escribe al menos un nombre de jugador:")
        owners, names = parseOwners(read())
    end

    local file = fs.open(OWNERS_FILE, "w")
    if not file then print("No se pudo guardar " .. OWNERS_FILE .. "; rol detenido por seguridad.") return nil end
    file.write(table.concat(names, "\n") .. "\n")
    file.close()
    print("Propietarios guardados en " .. OWNERS_FILE .. ".")
    return owners
end
local ROLE_DROPS = {
    { "cgs:flintlock", true }, { "cgs:revolver", true },
    { "cgs:shotgun", true }, { "cgs:nailgun", true },
    { "cgs:gatling", true }, { "cgs:blazegun", true },
    { "cgs:launcher", true }, { "cgs:item_launcher", true },
    { "cgs:ballistazooka", true }, { "cgs:hammer", true }, { "cgs:axe", true },
    { "cgs:paper_cartridge" }, { "cgs:paper_shot" },
    { "cgs:round_gatling_blank" }, { "cgs:round_gatling_piercing" }, { "cgs:round_gatling" },
    { "cgs:round_revolver_blank" }, { "cgs:round_revolver_piercing" }, { "cgs:round_revolver" },
    { "cgs:round_shotgun_flechette_steel" }, { "cgs:round_shotgun_flechette" },
    { "cgs:round_shotgun_incendiary" }, { "cgs:round_shotgun_blank" }, { "cgs:round_shotgun" },
    { "cgs:nail_steel" }, { "cgs:nail" }, { "cgs:rocket_small" }, { "cgs:rocket" },
    { "cgs:spear" }, { "cgs:lead_balls" }, { "cgs:shell_gatling" },
    { "cgs:shell_revolver" }, { "cgs:shell_shotgun" },
}
local ROLE_WEAPONS = {}
for _, drop in ipairs(ROLE_DROPS) do
    if drop[2] then ROLE_WEAPONS[drop[1]] = true end
end
local nextDrop = 1

local BLOCK_TYPES = {
    "minecraft:diamond_ore",
    "minecraft:deepslate_diamond_ore",
    "minecraft:ancient_debris",
    "minecraft:iron_ore",
    "minecraft:deepslate_iron_ore",
    "minecraft:gold_ore",
    "minecraft:deepslate_gold_ore",
}

local HOSTILE_TYPES = {
    "zombie", "skeleton", "spider", "creeper", "witch", "drowned",
    "husk", "stray", "phantom", "pillager", "vindicator", "evoker",
    "ravager", "cave_spider", "silverfish", "blaze", "ghast",
    "magma_cube", "hoglin", "zoglin", "wither_skeleton", "guardian",
    "elder_guardian", "shulker", "vex", "warden", "endermite", "slime",
    "piglin_brute",

    -- Alex's Mobs: entidades hostiles; se excluyen animales agresivos condicionales.
    "alexsmobs:bone_serpent", "alexsmobs:crimson_mosquito",
    "alexsmobs:centipede_head", "alexsmobs:mimicube", "alexsmobs:soul_vulture",
    "alexsmobs:guster", "alexsmobs:warped_mosco", "alexsmobs:straddler",
    "alexsmobs:dropbear", "alexsmobs:void_worm", "alexsmobs:rocky_roller",
    "alexsmobs:skelewag", "alexsmobs:farseer", "alexsmobs:skreecher",
    "alexsmobs:murmur",

    -- Ars Nouveau: Wilden salvajes.
    "ars_nouveau:wilden_hunter", "ars_nouveau:wilden_stalker",
    "ars_nouveau:wilden_guardian", "ars_nouveau:wilden_boss",

    -- Northstar: amenazas planetarias.
    "northstar:frozen_zombie", "northstar:mars_worm", "northstar:mars_cobra",
    "northstar:mercury_raptor", "northstar:venus_mimic", "northstar:venus_scorpion",

    -- Quark; Foxhound excluido porque puede estar domesticado.
    "quark:forgotten", "quark:wraith",
}

local function waitForStop()
    while true do
        local _, key = os.pullEvent("key")
        if key == keys.enter then return end
    end
end

local function report(message)
    pcall(android.sendChatMessage, message)
    print(message)
end

local function itemInHand()
    local ok, name = pcall(android.getHandInfo, "main")
    if not ok or type(name) ~= "string" or name == "" or name:lower() == "minecraft:air" or name:lower() == "air" then return nil end
    return name:lower()
end

local function slotName(index)
    local ok, name = pcall(android.getSlotInfo, index)
    if not ok or type(name) ~= "string" then return nil end
    return name:lower()
end

local function emptySlot(exceptA, exceptB)
    for index = 0, 8 do
        if index ~= FUEL_SLOT and index ~= exceptA and index ~= exceptB then
            local name = slotName(index)
            if not name or name == "air" or name == "minecraft:air" then return index end
        end
    end
end

local function lootOneRoleItem()
    local drop = ROLE_DROPS[nextDrop]
    nextDrop = nextDrop % #ROLE_DROPS + 1

    local held = itemInHand()
    if held and not ROLE_WEAPONS[held] then return end

    local destination
    if not drop[2] then
        for _, index in ipairs(AMMO_SLOTS) do
            local name = slotName(index)
            if not name or name == "air" or name == "minecraft:air" then destination = index break end
        end
        destination = destination or emptySlot()
    elseif held then
        destination = emptySlot()
    end
    if (not drop[2] or held) and not destination then return end

    local temporary
    if held then
        temporary = emptySlot(destination)
        if not temporary then return end
        local ok, result = pcall(android.storeItem, temporary)
        if not ok or result == true or (type(result) == "table" and result[1] == true) then return end
    end

    local ok, result = pcall(android.pickup, drop[1])
    local picked = itemInHand()
    local failed = not ok or result == true or (type(result) == "table" and result[1] == true)
    if not failed and picked then
        if drop[2] and not temporary then
            report("Recogí " .. picked .. "; queda equipada.")
            return
        end
        local stored, storeResult = pcall(android.storeItem, destination)
        if stored and storeResult ~= true and not (type(storeResult) == "table" and storeResult[1] == true) then
            report("Recogí " .. picked .. " en ranura " .. destination .. ".")
        else
            report("No pude guardar " .. picked .. "; la dejo en la mano.")
            if temporary then pcall(android.equipSlot, temporary) end
            return
        end
    end

    if temporary then pcall(android.equipSlot, temporary) end
end

local function refuelSafely()
    local fuelName = slotName(FUEL_SLOT)
    if not fuelName or not fuelName:find("redstone", 1, true) then return false end
    local held = itemInHand()
    local temporary
    if held then
        if not ROLE_WEAPONS[held] then return false end
        temporary = emptySlot()
        if not temporary then return false end
        local ok, result = pcall(android.storeItem, temporary)
        if not ok or result == true or (type(result) == "table" and result[1] == true) then return false end
    end
    local equipped, equipResult = pcall(android.equipSlot, FUEL_SLOT)
    if not equipped or equipResult == true or (type(equipResult) == "table" and equipResult[1] == true) then
        if temporary then pcall(android.equipSlot, temporary) end
        return false
    end
    local before = android.fuelLevel()
    local ok, result = pcall(android.refuel, 1)
    local after = android.fuelLevel()
    if itemInHand() then pcall(android.storeItem, FUEL_SLOT) end
    if temporary then pcall(android.equipSlot, temporary) end
    return ok and result ~= true and not (type(result) == "table" and result[1] == true) and after > before
end

local function scan()
    local findings = 0

    for _, blockType in ipairs(BLOCK_TYPES) do
        local blocks = android.getBlocksOfType(blockType) or {}
        for _, block in ipairs(blocks) do
            if findings < 3 and block.x and block.y and block.z then
                report("RECON: " .. blockType .. " en " .. math.floor(block.x) .. ", " .. math.floor(block.y) .. ", " .. math.floor(block.z))
                findings = findings + 1
            end
        end
        if findings >= 3 then break end
    end

    if findings == 0 then
        for _, mobType in ipairs(HOSTILE_TYPES) do
            local mob = android.getClosestMob(mobType)
            if mob and mob.uuid then
                report("ALERTA RECON: " .. mobType .. " detectado cerca de la patrulla.")
                break
            end
        end
    end
end

local function listenForCommands(unit, state, owners)
    local unitName = tostring(unit.name or "Android")
    while true do
        local _, data = os.pullEvent("onChatMessage")
        if type(data) == "table" and owners[tostring(data.senderName or ""):lower()] then
            local target, command = tostring(data.message or ""):match("^!jarvis%s+(.+)%s+(%S+)$")
            if target and command and (target:lower() == "all" or target:lower() == unitName:lower()) then
                command = command:lower()
                if command == "hold" then
                    state.mode = "hold"
                    android.cancelTask()
                elseif command == "resume" or command == "patrol" then
                    state.mode = "patrol"
                elseif command == "return" then
                    state.mode = "return"
                    android.cancelTask()
                elseif command == "status" then
                    local current = android.getSelf()
                    pcall(android.sendChatMessage, unitName .. " | Explorador | modo=" .. state.mode .. " | salud=" .. tostring(current.health) .. " | fuel=" .. tostring(android.fuelLevel()))
                end
            end
        end
    end
end

local function patrol(home, state)
    local points = {
        { x = home.x + PATROL_RADIUS, y = home.y, z = home.z },
        { x = home.x, y = home.y, z = home.z + PATROL_RADIUS },
        { x = home.x - PATROL_RADIUS, y = home.y, z = home.z },
        { x = home.x, y = home.y, z = home.z - PATROL_RADIUS },
    }
    local nextPoint = 1
    local lastRefuelAttempt = -5
    local lastReport = -REPORT_DELAY
    local lastLootScan = -1

    print("Rol Explorador activo: reconocimiento y recogida ligera de armas/munición CG.")
    print("Munición prioritaria en ranuras 1-3; repostaje opcional desde ranura 7.")
    print("Pulsa ENTER para detener.")
    while true do
        local task = android.currentTask()
        local self = android.getSelf()

        if self.health and self.health <= RETREAT_HEALTH and task ~= "idle" then
            android.cancelTask()
            task = "idle"
            report("ALERTA: Explorador herido; regresando al punto de inicio.")
        end

        if task == "idle" then
            if os.clock() - lastLootScan >= 0.8 then
                lootOneRoleItem()
                lastLootScan = os.clock()
            end
            local fuel = android.fuelLevel()
            if fuel < REFUEL_AT and os.clock() - lastRefuelAttempt >= 5 then
                local refueled = refuelSafely()
                lastRefuelAttempt = os.clock()
                fuel = android.fuelLevel()
                if refueled then
                    print("Explorador repostado desde la ranura " .. FUEL_SLOT .. ".")
                end
            end

            if fuel < 1 then
                sleep(SCAN_DELAY)
            elseif self.health and self.health <= RETREAT_HEALTH or state.mode == "return" then
                android.moveTo(home)
            elseif state.mode == "hold" then
                -- Mantiene posición y escucha la orden de reanudar.
            else
                if os.clock() - lastReport >= REPORT_DELAY then
                    scan()
                    lastReport = os.clock()
                end
                android.moveTo(points[nextPoint])
                nextPoint = nextPoint % #points + 1
            end
        end
        sleep(SCAN_DELAY)
    end
end

local function main()
    local self = android.getSelf()
    local home = { x = self.posX, y = self.posY, z = self.posZ }
    local state = { mode = "patrol" }
    local owners = loadOwners()
    if not owners then return end
    parallel.waitForAny(
        function() patrol(home, state) end,
        waitForStop,
        function() listenForCommands(self, state, owners) end
    )
    android.cancelTask()
    print("Explorador detenido.")
end

local ok, err = pcall(main)
if not ok then
    pcall(android.cancelTask)
    print("Error en Explorador: " .. tostring(err))
end
