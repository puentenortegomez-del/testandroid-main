-- ============================================================
-- ROL GUARDIA CIVIL - MELEE
-- CC:Androids 1.3.0 / Forge 1.20.1
--
-- Variante cuerpo a cuerpo del rol Fusilero.
-- La unidad patrulla, detecta hostiles y ataca con android.attack()
-- cuando se encuentra a distancia de melee.
-- Este archivo mantiene melee intencionadamente; androidscompat no convierte
-- attack() en disparo. Para armas NTGL usa fusilero.lua o guardia_civil.lua.
-- Este rol conserva melee intencionadamente; la recarga automatica y el
-- equipamiento de armadura/mochila pertenecen a los roles NTGL del addon.
-- ============================================================

local SCAN_DELAY = 0.8
local PATROL_RADIUS = 6
local COMBAT_RANGE_SQ = 12 * 12
local MELEE_RANGE_SQ = 3 * 3
local ATTACK_COOLDOWN = 0.65

local REFUEL_AT = 20
local REFUEL_RETRY_DELAY = 5
local FUEL_SLOT = 7
local HEAL_AT = 12
local HEAL_RETRY_DELAY = 8
local RETREAT_HEALTH = 6
local ALERT_DELAY = 10
local PICKUP_SCAN_DELAY = 0.8
local OWNERS_FILE = "owners.cfg"

-- Equipo permitido para el agente. Cambia o amplía esta lista según tus mods.
local MELEE_WEAPONS = {
    "minecraft:wooden_sword",
    "minecraft:stone_sword",
    "minecraft:iron_sword",
    "minecraft:golden_sword",
    "minecraft:diamond_sword",
    "minecraft:netherite_sword",
    "minecraft:iron_axe",
    "minecraft:diamond_axe",
    "minecraft:netherite_axe",
}

local FOOD_ITEMS = {
    "minecraft:cooked_beef", "minecraft:cooked_porkchop",
    "minecraft:cooked_chicken", "minecraft:cooked_mutton",
    "minecraft:golden_carrot", "minecraft:golden_apple",
    "minecraft:bread", "minecraft:cooked_cod", "minecraft:cooked_salmon",
}

local POTION_ITEMS = { "minecraft:potion", "minecraft:splash_potion" }

local TARGET_TYPES = {
    "minecraft:zombie", "minecraft:skeleton", "minecraft:spider",
    "minecraft:creeper", "minecraft:witch", "minecraft:drowned",
    "minecraft:husk", "minecraft:stray", "minecraft:pillager",
    "minecraft:vindicator", "minecraft:evoker", "minecraft:ravager",
    "minecraft:cave_spider", "minecraft:silverfish", "minecraft:blaze",
    "minecraft:ghast", "minecraft:magma_cube", "minecraft:hoglin",
    "minecraft:zoglin", "minecraft:wither_skeleton", "minecraft:vex",
    "minecraft:warden", "minecraft:endermite", "minecraft:slime",
    "minecraft:piglin_brute", "alexsmobs:bone_serpent",
    "alexsmobs:crimson_mosquito", "alexsmobs:centipede_head",
    "alexsmobs:mimicube", "alexsmobs:soul_vulture", "alexsmobs:guster",
    "alexsmobs:warped_mosco", "alexsmobs:straddler", "alexsmobs:dropbear",
    "alexsmobs:void_worm", "alexsmobs:rocky_roller", "alexsmobs:skelewag",
    "alexsmobs:farseer", "alexsmobs:skreecher", "alexsmobs:murmur",
    "ars_nouveau:wilden_hunter", "ars_nouveau:wilden_stalker",
    "ars_nouveau:wilden_guardian", "ars_nouveau:wilden_boss",
    "northstar:frozen_zombie", "northstar:mars_worm", "northstar:mars_cobra",
    "northstar:mercury_raptor", "northstar:venus_mimic",
    "northstar:venus_scorpion", "quark:forgotten", "quark:wraith",
}

local lastAttack = 0
local lastTarget = nil
local roleDropIndex = 1

local ROLE_DROPS = {}
for _, item in ipairs(MELEE_WEAPONS) do
    ROLE_DROPS[#ROLE_DROPS + 1] = { match = item, kind = "weapon" }
end
for _, item in ipairs(FOOD_ITEMS) do
    ROLE_DROPS[#ROLE_DROPS + 1] = { match = item, kind = "food" }
end
for _, item in ipairs(POTION_ITEMS) do
    ROLE_DROPS[#ROLE_DROPS + 1] = { match = item, kind = "potion" }
end

local function apiFailed(value)
    return type(value) == "table" and value[1] == true
end

local function slotInfo(index)
    local ok, name, occupied = pcall(android.getSlotInfo, index)
    if not ok then return nil, false end
    local normalized = type(name) == "string" and name:lower() or ""
    local exists = normalized ~= "" and normalized ~= "minecraft:air" and normalized ~= "air"
    if type(occupied) == "boolean" then exists = exists and occupied end
    return name, exists
end

local function handInfo()
    local ok, name, count = pcall(android.getHandInfo, "main")
    if not ok or type(name) ~= "string" then return nil, 0 end
    local lower = name:lower()
    if lower == "" or lower == "air" or lower == "minecraft:air" then return nil, 0 end
    return name, tonumber(count) or 0
end

local function matchesList(name, list)
    if type(name) ~= "string" then return false end
    local lower = name:lower()
    for _, item in ipairs(list) do
        if lower:find(item, 1, true) then return true end
    end
    return false
end

local function findEmptySlot(exclude)
    for index = 0, 8 do
        if index ~= exclude then
            local _, occupied = slotInfo(index)
            if not occupied then return index end
        end
    end
    return nil
end

local function findSlotWithItem(list)
    for index = 0, 8 do
        local name, occupied = slotInfo(index)
        if occupied and matchesList(name, list) then return index end
    end
    return nil
end

local function attackAt(target)
    if not target or not target.uuid then return false end
    local weapon = handInfo()
    if not weapon or not matchesList(weapon, MELEE_WEAPONS) then return false end
    local now = os.clock()
    if now - lastAttack < ATTACK_COOLDOWN then return false end
    local ok, result = pcall(android.attack)
    lastAttack = now
    return ok and not apiFailed(result)
end

local function engage(target)
    local self = android.getSelf()
    if not self or not target then return end
    local dx, dy, dz = target.posX - self.posX, target.posY - self.posY, target.posZ - self.posZ
    local distSq = dx * dx + dy * dy + dz * dz
    if distSq > COMBAT_RANGE_SQ then
        android.moveTo({ x = target.posX, y = target.posY, z = target.posZ })
    elseif distSq > MELEE_RANGE_SQ then
        android.moveTo({ x = target.posX, y = target.posY, z = target.posZ })
    else
        attackAt(target)
    end
end

local function tryHeal()
    local self = android.getSelf()
    if not self or not self.health or self.health >= HEAL_AT then return end
    local held = handInfo()
    local tempSlot
    if held then
        tempSlot = findEmptySlot()
        if not tempSlot then return end
        local ok, result = pcall(android.storeItem, tempSlot)
        if not ok or apiFailed(result) then return end
    end
    local targetSlot = findSlotWithItem(POTION_ITEMS) or findSlotWithItem(FOOD_ITEMS)
    if targetSlot then
        pcall(android.equipSlot, targetSlot)
        print("Equipo médico preparado. HP=" .. tostring(self.health))
    end
    if tempSlot and not handInfo() then pcall(android.equipSlot, tempSlot) end
end

local function collectRoleDrops()
    local drop = ROLE_DROPS[roleDropIndex]
    roleDropIndex = roleDropIndex % #ROLE_DROPS + 1
    if handInfo() or not drop then return end
    local ok, result = pcall(android.pickup, drop.match)
    if not ok or apiFailed(result) then return end
    local picked = handInfo()
    if not picked then return end
    if drop.kind == "weapon" then
        print("Porra/arma recogida: " .. tostring(picked))
        return
    end
    local destination = findEmptySlot()
    if destination then pcall(android.storeItem, destination) end
end

local function findFuelSlot()
    local name, occupied = slotInfo(FUEL_SLOT)
    if occupied and name and name:lower():find("redstone", 1, true) then return FUEL_SLOT end
    return nil
end

local function refuel()
    local fuelSlot = findFuelSlot()
    if not fuelSlot or handInfo() then return false end
    local ok, result = pcall(android.equipSlot, fuelSlot)
    if not ok or apiFailed(result) then return false end
    local before = android.fuelLevel()
    pcall(android.refuel, 1)
    return android.fuelLevel() > before
end

local function findHostile()
    local self = android.getSelf()
    local closest, closestDistance
    for _, mobType in ipairs(TARGET_TYPES) do
        local ok, mob = pcall(android.getClosestMob, mobType)
        if ok and type(mob) == "table" and mob.uuid and mob.posX and mob.posY and mob.posZ then
            local dx, dy, dz = mob.posX - self.posX, mob.posY - self.posY, mob.posZ - self.posZ
            local distSq = dx * dx + dy * dy + dz * dz
            if not closestDistance or distSq < closestDistance then
                closest, closestDistance = mob, distSq
            end
        end
    end
    return closest
end

local function parseOwners(input)
    local owners, names = {}, {}
    for part in tostring(input):gmatch("[^,\n]+") do
        local name = part:match("^%s*(.-)%s*$")
        if name ~= "" and not owners[name:lower()] then
            owners[name:lower()] = true
            names[#names + 1] = name
        end
    end
    return owners, names
end

local function loadOwners()
    if fs.exists(OWNERS_FILE) then
        local file = fs.open(OWNERS_FILE, "r")
        if not file then return nil end
        local owners, names = parseOwners(file.readAll())
        file.close()
        return #names > 0 and owners or nil
    end
    print("Agentes autorizados, separados por comas:")
    local owners, names = parseOwners(read())
    while #names == 0 do owners, names = parseOwners(read()) end
    local file = fs.open(OWNERS_FILE, "w")
    if not file then return nil end
    file.write(table.concat(names, "\n") .. "\n")
    file.close()
    return owners
end

local function listenForCommands(unit, state, owners)
    local unitName = tostring(unit.name or "GuardiaCivil")
    while true do
        local _, data = os.pullEvent("onChatMessage")
        if type(data) == "table" and owners[tostring(data.senderName or ""):lower()] then
            local target, command = tostring(data.message or ""):match("^!guardia%s+(.+)%s+(%S+)$")
            if target and command and (target:lower() == "all" or target:lower() == unitName:lower()) then
                command = command:lower()
                if command == "hold" then state.mode = "hold"; android.cancelTask()
                elseif command == "resume" or command == "patrol" then state.mode = "patrol"
                elseif command == "return" then state.mode = "return"; android.cancelTask()
                elseif command == "status" then
                    local cur = android.getSelf()
                    pcall(android.sendChatMessage, unitName .. " | Guardia Civil | " .. state.mode .. " | HP=" .. tostring(cur.health) .. " | fuel=" .. tostring(android.fuelLevel()) .. " | melee=" .. tostring(handInfo() or "-"))
                end
            end
        end
    end
end

local function patrol(home, state, unit)
    local points = {
        { x = home.x + PATROL_RADIUS, y = home.y, z = home.z },
        { x = home.x, y = home.y, z = home.z + PATROL_RADIUS },
        { x = home.x - PATROL_RADIUS, y = home.y, z = home.z },
        { x = home.x, y = home.y, z = home.z - PATROL_RADIUS },
    }
    local nextPoint, lastFuel, lastDrop, lastHeal = 1, -REFUEL_RETRY_DELAY, -PICKUP_SCAN_DELAY, -HEAL_RETRY_DELAY
    print("Guardia Civil melee activa. Comandos: !guardia " .. tostring(unit.name or "GuardiaCivil") .. " hold|resume|return|status")
    while true do
        local self, task = android.getSelf(), android.currentTask()
        if self.health and self.health <= RETREAT_HEALTH and task ~= "idle" then android.cancelTask(); task = "idle" end
        if task == "idle" then
            if os.clock() - lastHeal >= HEAL_RETRY_DELAY then tryHeal(); lastHeal = os.clock() end
            if os.clock() - lastDrop >= PICKUP_SCAN_DELAY then collectRoleDrops(); lastDrop = os.clock() end
            if android.fuelLevel() < REFUEL_AT and os.clock() - lastFuel >= REFUEL_RETRY_DELAY then refuel(); lastFuel = os.clock() end
            if state.mode == "return" or (self.health and self.health <= RETREAT_HEALTH) then
                lastTarget = nil; android.moveTo(home)
            elseif state.mode == "patrol" then
                local target = findHostile()
                if target then lastTarget = target.uuid; engage(target)
                else lastTarget = nil; android.moveTo(points[nextPoint]); nextPoint = nextPoint % #points + 1 end
        end
        sleep(SCAN_DELAY)
    end
end

local function main()
    local self = android.getSelf()
    local home = { x = self.posX, y = self.posY, z = self.posZ }
    local owners = loadOwners()
    if not owners then print("No se pudieron cargar los agentes autorizados."); return end
    local state = { mode = "patrol" }
    parallel.waitForAny(
        function() patrol(home, state, self) end,
        function() while true do local _, key = os.pullEvent("key"); if key == keys.enter then return end end end,
        function() listenForCommands(self, state, owners) end
    )
    android.cancelTask()
    print("Guardia Civil melee detenida.")
end

local ok, err = pcall(main)
if not ok then pcall(android.cancelTask); print("Error: " .. tostring(err)) end
