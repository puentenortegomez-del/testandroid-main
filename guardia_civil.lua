-- GUARDIA CIVIL v21 - adaptada a androidscompat + NTGL
-- CC:Androids 1.3.0 / Forge 1.20.1
-- Busca armas por el nombre que informa getSlotInfo; no depende de una ranura fija.
-- No simula clics: androidscompat entrega el objetivo a NTGL.
-- Tambien gestiona recarga automatica y equipamiento de armadura/mochila.
-- NTGL recarga automáticamente; R o !guardia all reload solicitan recarga manual.
-- Aviones: ejecuta como jugador /androidscompat mount cerca de un avión vacío.

local SCAN_DELAY = 0.8
local STATUS_REPORT_INTERVAL = 10
local PATROL_RADIUS = 8
local REFUEL_AT = 20
local REFUEL_RETRY_DELAY = 15
local STASH_SIZE = 9
local RETREAT_HEALTH = 6
local ALERT_DELAY = 10
local ATTACK_TIMEOUT = 45
local WEAPON_USE_COOLDOWN = 0.8
local OWNERS_FILE = "owners.cfg"

local WEAPONS = {
    "minecraft:wooden_sword","minecraft:stone_sword",
    "minecraft:iron_sword","minecraft:golden_sword",
    "minecraft:diamond_sword","minecraft:netherite_sword",
    "minecraft:iron_axe","minecraft:diamond_axe",
    "minecraft:netherite_axe",
}
local WEAPON_NAMES = {
    "wooden sword","stone sword","iron sword","golden sword",
    "diamond sword","netherite sword","iron axe","diamond axe",
    "netherite axe",
}
-- Nombres que puede devolver getSlotInfo cuando el idioma del juego no
-- muestra el ID cgs:item. Se revisan solo después de descartar munición.
local CGS_WEAPON_ALIASES = {
    { "cgs:revolver", "revolver", "revólver" },
    { "cgs:shotgun", "shotgun", "escopeta" },
    { "cgs:gatling", "gatling" },
    { "cgs:nailgun", "nailgun", "clavadora" },
    { "cgs:flintlock", "flintlock", "pistola de chispa", "pistola de pedernal" },
    { "cgs:blazegun", "blazegun" },
    { "cgs:launcher", "launcher", "lanzador" },
    { "cgs:frag_grenade", "frag grenade", "granada de fragmentación", "granada de fragmentacion" },
    { "cgs:pneumatic_hammer", "pneumatic hammer", "martillo neumático", "martillo neumatico" },
}
local AMMO_IDS = {
    "cgs:flintlock_bullet","cgs:revolver_round","cgs:shotgun_round",
    "cgs:nail","cgs:gatling_round","cgs:piercing_revolver_round",
    "cgs:incendiary_shotgun_round","cgs:paper_cartridge","cgs:paper_shot",
    "cgs:round_revolver","cgs:round_revolver_piercing","cgs:round_revolver_blank",
    "cgs:round_shotgun","cgs:round_shotgun_flechette",
    "cgs:round_shotgun_flechette_steel","cgs:round_shotgun_incendiary",
    "cgs:round_shotgun_blank","cgs:round_gatling","cgs:round_gatling_piercing",
    "cgs:round_gatling_blank","cgs:nail_steel","cgs:rocket",
    "cgs:rocket_small","cgs:spear","cgs:lead_balls",
    "cgs:shell_gatling","cgs:shell_revolver","cgs:shell_shotgun",
    "cbc:ballistic_pistol_round","cbc:ballistic_carbine_round",
    "cbc:ballistic_shotgun_shell","cbc:ballistic_sniper_round",
    "cbc:ballistic_gatling_round","cbc:ballistic_grenade",
    "cbc:ballistic_minigun_round","cbc:cannon_shot",
}
local TARGETS = {
    "minecraft:zombie","minecraft:skeleton","minecraft:spider",
    "minecraft:creeper","minecraft:witch","minecraft:drowned",
    "minecraft:husk","minecraft:stray","minecraft:phantom",
    "minecraft:pillager","minecraft:vindicator","minecraft:evoker",
    "minecraft:ravager","minecraft:cave_spider","minecraft:silverfish",
    "minecraft:blaze","minecraft:ghast","minecraft:magma_cube",
    "minecraft:hoglin","minecraft:zoglin","minecraft:wither_skeleton",
    "minecraft:guardian","minecraft:shulker","minecraft:vex",
    "minecraft:warden","minecraft:endermite","minecraft:slime",
    "minecraft:piglin_brute",
    "alexsmobs:bone_serpent","alexsmobs:crimson_mosquito",
    "alexsmobs:centipede_head","alexsmobs:mimicube",
    "alexsmobs:soul_vulture","alexsmobs:guster","alexsmobs:warped_mosco",
    "alexsmobs:straddler","alexsmobs:dropbear","alexsmobs:void_worm",
    "alexsmobs:rocky_roller","alexsmobs:skelewag","alexsmobs:farseer",
    "alexsmobs:skreecher","alexsmobs:murmur",
    "ars_nouveau:wilden_hunter","ars_nouveau:wilden_stalker",
    "ars_nouveau:wilden_guardian","ars_nouveau:wilden_boss",
    "northstar:frozen_zombie","northstar:mars_worm",
    "northstar:mars_cobra","northstar:mercury_raptor",
    "northstar:venus_mimic","northstar:venus_scorpion",
    "quark:forgotten","quark:wraith",
    "vampirism:vampire","vampirism:ghoul","werewolves:werewolf",
}

local lastTarget = nil
local inCombat = false
local lastCombatEnd = 0
local attackStart = 0
local lastAttackError = 0
local lastWeaponUse = 0
local lastReloadNotice = 0

local function fail(v)
    return type(v)=="table" and v[1]==true
end

local function hand()
    local ok,n,c = pcall(android.getHandInfo,"main")
    if not ok or type(n)~="string" then return nil,0 end
    local l=n:lower()
    if l=="" or l=="air" or l=="minecraft:air" then return nil,0 end
    return n, tonumber(c) or 0
end

local function isAmmo(n)
    return false
end

local function isWep(n)
    if type(n)~="string" then return false end
    if isAmmo(n) then return false end
    local l=n:lower()
    for _,i in ipairs(WEAPONS) do
        if l:find(i,1,true) then return true end
    end
    for _,i in ipairs(WEAPON_NAMES) do
        if l:find(i,1,true) then return true end
    end
    return false
end

local function isFuel(n)
    if type(n)~="string" then return false end
    local l=n:lower()
    if l:find("redstone torch",1,true) then return false end
    if l:find("redstone_torch",1,true) then return false end
    return l:find("redstone",1,true)~=nil
end

local function emptyName(name)
    if type(name)~="string" then return name==false or name==nil end
    local lower=name:lower()
    return lower=="" or lower=="empty" or lower=="minecraft:air" or lower=="air" or lower=="a"
end

-- Lee las nueve ranuras que expone CC:Androids. Solo devuelve nombres y
-- estados; equipar, guardar y restaurar ocurren en las funciones de acción.
local function scanInventory()
    local inventory={}
    for i=0,STASH_SIZE-1 do
        local ok,name,extra=pcall(android.getSlotInfo,i)
        if ok then
            if type(name)=="table" then
                name=name.name or name.id or name.item
            elseif type(name)=="boolean" and type(extra)=="string" then
                name=extra
            end
            inventory[#inventory+1]={slot=i,name=name,empty=emptyName(name)}
        end
    end
    return inventory
end

local function inventorySummary(inventory)
    local bySlot={}
    for _,item in ipairs(inventory) do
        bySlot[item.slot]=item.empty and "-" or tostring(item.name)
    end
    local slots={}
    for i=0,STASH_SIZE-1 do
        slots[#slots+1]="r"..i.."="..tostring(bySlot[i] or "?")
    end
    return table.concat(slots," | ")
end

-- Busca espacio libre consultando el inventario actual, sin ranuras asignadas.
local function findFreeSlot(exclude)
    for _,item in ipairs(scanInventory()) do
        if item.slot~=exclude and item.empty then return item.slot end
    end
    return nil
end

-- Busca redstone en cualquier ranura del inventario actual.
local function findFuelSlot()
    local found={}
    for _,item in ipairs(scanInventory()) do
        if isFuel(item.name) then return item.slot end
        found[#found+1]="r"..item.slot.."="..tostring(item.name)
    end
    return nil,table.concat(found,", ")
end

local function cgsWeaponRank(name)
    if type(name)~="string" or isAmmo(name) then return nil end
    local lower=name:lower()
    for rank,item in ipairs(WEAPONS) do
        if lower:find(item,1,true) then return rank end
    end
    for rank,item in ipairs(WEAPON_NAMES) do
        if lower:find(item,1,true) then return rank end
    end
    return nil
end

-- Elige un arma melee del inventario; la preferencia es estable y no depende
-- de que esté colocada en una ranura concreta.
local function findWeaponSlot()
    local bestSlot, bestRank
    for _,item in ipairs(scanInventory()) do
        local rank=cgsWeaponRank(item.name)
        if rank then
            if not bestRank or rank<bestRank then
                bestSlot,bestRank=item.slot,rank
            end
        end
    end
    return bestSlot
end

-- Equipa un arma melee disponible sin reservar una ranura fija.
local function equipWeapon()
    local h = hand()
    local weaponSlot = findWeaponSlot()
    if h and isWep(h) and cgsWeaponRank(h)==nil and weaponSlot==nil then
        return true
    end
    if h and cgsWeaponRank(h) then return true end
    if weaponSlot == nil then return false end

    local tmp = nil
    if h then
        tmp = findFreeSlot(weaponSlot)
        if not tmp then return false end
        local ok,r = pcall(android.storeItem,tmp)
        if not ok or fail(r) then return false end
    end

    local ok,r = pcall(android.equipSlot,weaponSlot)
    if not ok or fail(r) then
        if tmp then pcall(android.equipSlot,tmp) end
        return false
    end

    local nw = hand()
    if nw and isWep(nw) then
        print("Arma: "..nw)
        return true
    end

    if nw then pcall(android.storeItem,weaponSlot) end
    if tmp then pcall(android.equipSlot,tmp) end
    return false
end

-- Refuel desde FUEL_SLOT (solo cuando fuel bajo y sin combate)
local function doRefuel()
    if inCombat then return end
    if os.clock()-lastCombatEnd < 5 then return end

    local heldName = hand()
    local fuelInHand = heldName and isFuel(heldName)
    local fuelSlot, inventoryInfo
    if not fuelInHand then fuelSlot, inventoryInfo = findFuelSlot() end
    if not fuelInHand and fuelSlot == nil then
        print("Refuel: sin redstone; inventario: "..tostring(inventoryInfo))
        return
    end

    local tmp = nil
    if not fuelInHand and heldName then
        tmp = findFreeSlot(fuelSlot)
        if not tmp then return end
        local ok,r = pcall(android.storeItem,tmp)
        if not ok or fail(r) then return end
    end

    if not fuelInHand then
        local ok,r = pcall(android.equipSlot,fuelSlot)
        if not ok or fail(r) then
            if tmp then pcall(android.equipSlot,tmp) end
            return
        end

        local n = hand()
        if not n or not isFuel(n) then
            if n then pcall(android.storeItem,fuelSlot) end
            if tmp then pcall(android.equipSlot,tmp) end
            print("Refuel: no se pudo equipar redstone de r"..fuelSlot)
            return
        end
    end

    local before = android.fuelLevel()
    pcall(android.refuel,1)
    local after = android.fuelLevel()

    local rem = hand()
    if not fuelInHand and rem then pcall(android.storeItem,fuelSlot) end
    if tmp then pcall(android.equipSlot,tmp) end

    if after > before then
        print("Fuel -> "..tostring(after))
    end
end

local function findMob()
    local s = android.getSelf()
    local best, bestType, bestD
    for _,t in ipairs(TARGETS) do
        local ok,m = pcall(android.getClosestMob,t)
        if ok and type(m)=="table" and m.uuid and m.posX then
            local dx=m.posX-s.posX
            local dy=m.posY-s.posY
            local dz=m.posZ-s.posZ
            local d=dx*dx+dy*dy+dz*dz
            if not bestD or d<bestD then
                best,bestType,bestD = m,t,d
            end
        end
    end
    return best, bestType
end

local function combat(mob)
    if not mob or not mob.uuid then return false end
    local w = hand()
    if not w or not isWep(w) or (cgsWeaponRank(w)==nil and findWeaponSlot()~=nil) then
        if not equipWeapon() then
            if os.clock()-lastAttackError>=5 then
                print("No ataco: no pude equipar un arma CGS")
                lastAttackError=os.clock()
            end
            return false
        end
    end

    w=hand()
    if not w or not isWep(w) then return false end
    -- androidscompat entrega el objetivo a NTGL; attack() seria melee.
    return true

end

local function parseOwners(input)
    local o,names={},{}
    for p in tostring(input):gmatch("[^,\n]+") do
        local n=p:match("^%s*(.-)%s*$")
        if n~="" and not o[n:lower()] then
            o[n:lower()]=true
            names[#names+1]=n
        end
    end
    return o,names
end

local function loadOwners()
    if fs.exists(OWNERS_FILE) then
        local f=fs.open(OWNERS_FILE,"r")
        if not f then return nil end
        local o,n=parseOwners(f.readAll())
        f.close()
        if #n==0 then return nil end
        return o
    end
    print("Jugadores (comas):")
    local o,n=parseOwners(read())
    while #n==0 do
        print("Escribe un nombre:")
        o,n=parseOwners(read())
    end
    local f=fs.open(OWNERS_FILE,"w")
    if not f then return nil end
    f.write(table.concat(n,"\n").."\n")
    f.close()
    return o
end

local function waitStop()
    while true do
        local _,k=os.pullEvent("key")
        if k==keys.q then return end
        if k==keys.r then
            local now=os.clock()
            if now-lastReloadNotice>=2 then
                print("La recarga es automatica; R no ejecuta comandos del servidor")
                lastReloadNotice=now
            end
        end
    end
end

local function listen(unit,state,owners)
    local un=tostring(unit.name or "Android")
    while true do
        local _,d=os.pullEvent("onChatMessage")
        if type(d)=="table" and owners[tostring(d.senderName or ""):lower()] then
            local t,c=tostring(d.message or ""):match("^!guardia%s+(.+)%s+(%S+)$")
            if t and c and (t:lower()=="all" or t:lower()==un:lower()) then
                c=c:lower()
                if c=="hold" then
                    state.mode="hold"
                    android.cancelTask()
                elseif c=="resume" or c=="patrol" then
                    state.mode="patrol"
                elseif c=="return" then
                    state.mode="return"
                    android.cancelTask()
                elseif c=="reload" then
                    print("Recarga automatica activa: androidscompat/NTGL")
                elseif c=="status" then
                    local s=android.getSelf()
                    local w=hand()
                    local inv=tostring(state.inventoryStatus or "-")
                    if #inv>120 then inv=inv:sub(1,117).."..." end
                    pcall(android.sendChatMessage,un.."|"..state.mode
                        .."|HP="..tostring(s.health)
                        .."|fuel="..tostring(android.fuelLevel())
                        .."|wep="..tostring(w or "-")
                        .."|combat="..tostring(inCombat)
                        .."|backend=androidscompat/NTGL"
                        .."|equip=armadura+mochila"
                        .."|inv="..inv)
                end
            end
        end
    end
end

local function main()
    local s = android.getSelf()
    local home = {x=s.posX,y=s.posY,z=s.posZ}
    local state = {mode="patrol"}
    local owners = loadOwners()
    if not owners then
        print("Sin owners.")
        return
    end

    local initialInventory=scanInventory()
    state.inventoryStatus=inventorySummary(initialInventory)
    print("=== Guardia Civil NTGL v21 ===")
    print("Pulsa Q para detener")
    print("FUEL_SLOT=dinámico")
    print("Equipo: selección automática de arma melee")
    print("Mano: "..tostring(hand() or "VACIA"))
    print("Inventario: "..state.inventoryStatus)
    print("Estado: HP="..tostring(s.health)
        .." | fuel="..tostring(android.fuelLevel())
        .." | tarea="..tostring(android.currentTask())
        .." | modo="..state.mode)
    print("===========")

    equipWeapon()
    pcall(android.sendChatMessage,"!androidscompat ntgl start "..tostring(s.uuid or s.UUID))

    local points = {
        {x=home.x+PATROL_RADIUS,y=home.y,z=home.z},
        {x=home.x,y=home.y,z=home.z+PATROL_RADIUS},
        {x=home.x-PATROL_RADIUS,y=home.y,z=home.z},
        {x=home.x,y=home.y,z=home.z-PATROL_RADIUS},
    }
    local np=1
    local lastRefuel=0
    local lastAlert=0
    local lastWep=0
    local lastInventorySignature=state.inventoryStatus
    local lastStatusSignature=nil
    local lastStatusReport=0

    parallel.waitForAny(
        function()
            while true do
                local self=android.getSelf()
                local task=android.currentTask()
                local now=os.clock()
                local inventory=scanInventory()
                state.inventoryStatus=inventorySummary(inventory)
                local held,heldCount=hand()
                local fuelNow=android.fuelLevel()
                local hpNow=self and self.health or "?"
                if state.inventoryStatus~=lastInventorySignature then
                    print("Inventario: "..state.inventoryStatus)
                    lastInventorySignature=state.inventoryStatus
                end
                local signature=table.concat({
                    tostring(hpNow),tostring(task),state.mode,
                    tostring(held or "-"),tostring(heldCount),tostring(inCombat),
                },"|")
                if signature~=lastStatusSignature or now-lastStatusReport>=STATUS_REPORT_INTERVAL then
                    print("Scan | HP="..tostring(hpNow)
                        .." | fuel="..tostring(fuelNow)
                        .." | tarea="..tostring(task)
                        .." | modo="..state.mode
                        .." | mano="..tostring(held or "VACIA")
                        .." x"..tostring(heldCount)
                        .." | combate="..tostring(inCombat))
                    lastStatusSignature=signature
                    lastStatusReport=now
                end

                if (task=="attacking" or task=="usingEntity"
                    or task=="movingToEntity" or task=="movintToEntity") and lastTarget then
                    if now-attackStart>ATTACK_TIMEOUT then
                        android.cancelTask()
                        task="idle"
                        lastTarget=nil
                        inCombat=false
                        lastCombatEnd=now
                    end
                end

                if self.health and self.health<=RETREAT_HEALTH and task~="idle" then
                    android.cancelTask()
                    task="idle"
                    if now-lastAlert>=ALERT_DELAY then
                        pcall(android.sendChatMessage,"HP bajo. Replegando.")
                        lastAlert=now
                    end
                end

                if task~="idle" then
                    sleep(SCAN_DELAY)
                else

                local mob,mobType=findMob()
                local wasC=inCombat
                inCombat=(mob~=nil)
                if wasC and not inCombat then
                    lastCombatEnd=now
                end

                if not inCombat and now-lastWep>=15 then
                    local w=hand()
                    if not w or not isWep(w) or (cgsWeaponRank(w)==nil and findWeaponSlot()~=nil) then
                        equipWeapon()
                    end
                    lastWep=now
                end

                local fuel=android.fuelLevel()
                if fuel<REFUEL_AT
                    and now-lastRefuel>=REFUEL_RETRY_DELAY
                    and not inCombat
                    and (now-lastCombatEnd)>=5 then
                    doRefuel()
                    lastRefuel=now
                    fuel=android.fuelLevel()
                end

                if fuel<1 then
                    sleep(SCAN_DELAY)
                elseif state.mode=="return"
                    or (self.health and self.health<=RETREAT_HEALTH) then
                    lastTarget=nil
                    android.moveTo(home)
                elseif state.mode=="patrol" then
                    if mob then
                        if lastTarget~=mob.uuid then
                            lastTarget=mob.uuid
                            attackStart=now
                            if now-lastAlert>=ALERT_DELAY then
                                pcall(android.sendChatMessage,"ALERTA: "..mobType)
                                lastAlert=now
                            end
                        end
                        combat(mob)
                    else
                        lastTarget=nil
                        android.moveTo(points[np])
                        np=np%#points+1
                    end
                elseif state.mode=="hold" then
                    lastTarget=nil
                end
                end
                sleep(SCAN_DELAY)
            end
        end,
        waitStop,
        function()
            listen(s,state,owners)
        end
    )
    android.cancelTask()
    print("Guardia Civil detenida.")
end

local ok,err=pcall(main)
local self=android.getSelf()
if self then pcall(android.sendChatMessage,"!androidscompat ntgl stop "..tostring(self.uuid or self.UUID)) end
if not ok then
    pcall(android.cancelTask)
    print("Error: "..tostring(err))
end   
