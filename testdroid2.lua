-- FUSILERO v24 - adaptado a androidscompat + NTGL
-- CC:Androids 1.3.0 / Create: Gunsmithing
--
-- Al arrancar imprime las funciones de "android" disponibles.
-- No simula clics de disparo desde Lua. androidscompat entrega los objetivos
-- a NTGL y también controla las armas de los aviones.
--   !jarvis all hold | patrol | return | status | test
--
-- Con androidscompat instalado, el disparo lo hace NTGL en servidor mediante
-- GunAttackGoal. Este Lua no llama attack/useEntity: ambos son interacciones
-- distintas del disparo NTGL.

local SCAN_DELAY      = 0.8
local PATROL_RADIUS   = 8
local REFUEL_AT       = 20
local REFUEL_RETRY    = 15
local STASH_SIZE      = 9
local RETREAT_HEALTH  = 6
local ALERT_DELAY     = 10
local FIRE_COOLDOWN   = 1.5   -- segundos entre disparos
local FIRE_HOLD       = 0.3   -- solo se usa en metodos de interaccion corta
local ADS_DELAY       = 0.5   -- espera entre pasos de una secuencia (ej: apuntar -> disparar)
local MAX_RANGE       = 18    -- si el mob esta mas lejos, te acercas
local STOP_RANGE      = 10    -- te acercas hasta esta distancia
local OWNERS_FILE     = "owners.cfg"
local ACCEPT_ANY_HELD = false -- no tratar cualquier objeto como si fuera un arma

-- Create: Gunsmithing / NTGL 3.1.2
-- Se prioriza cualquier arma cuyo ID pertenezca a Create: Gunsmithing.
local CGS_PREFIX = "cgs:"
local CGS_WEAPON_IDS = {
    "cgs:flintlock", "cgs:revolver", "cgs:shotgun", "cgs:nailgun",
    "cgs:gatling", "cgs:blazegun", "cgs:launcher", "cgs:hammer",
    "cgs:frag_grenade",
}

-- attack() en CC:Androids es cuerpo a cuerpo y useEntity() es clic derecho.
-- Ninguno de los dos dispara un arma NTGL.
-- Estos nombres se prueban por si alguna versión/parche expone un puente real
-- de disparo. En CC:Androids 1.3.0 oficial no existe ninguno.
local FIRE_CANDIDATES = {
    "shootEntity", "shoot", "ntglShoot", "leftClickEntity", "leftClick",
}
local TAKES_UUID = {
    shootEntity = true, ntglShoot = true, leftClickEntity = true,
}
local FORBIDDEN_FIRE_METHODS = {
    attack = true, useEntity = true, use = true, useItem = true,
}
local ALLOWED_FIRE_METHODS = {}
for _,n in ipairs(FIRE_CANDIDATES) do ALLOWED_FIRE_METHODS[n]=true end

local TARGETS = {
    "minecraft:zombie","minecraft:skeleton","minecraft:spider","minecraft:creeper",
    "minecraft:witch","minecraft:drowned","minecraft:husk","minecraft:stray",
    "minecraft:phantom","minecraft:pillager","minecraft:vindicator","minecraft:evoker",
    "minecraft:ravager","minecraft:cave_spider","minecraft:silverfish","minecraft:blaze",
    "minecraft:ghast","minecraft:magma_cube","minecraft:hoglin","minecraft:zoglin",
    "minecraft:wither_skeleton","minecraft:guardian","minecraft:shulker","minecraft:vex",
    "minecraft:warden","minecraft:endermite","minecraft:slime","minecraft:piglin_brute",
}

local WEAPON_ORDER = {
    "revolver","shotgun","gatling","nailgun","flintlock","blazegun",
    "launcher","frag_grenade","pneumatic_hammer",
}
local AMMO_WORDS = { "round","bullet","cartridge","shell","nail_","rocket","spear","lead_balls","paper_shot" }

local fireMethod = nil
local lastFire = 0
local lastTarget = nil
local inCombat = false

-- ---------------------------------------------------------------- utilidades
local function fail(v) return type(v)=="table" and v[1]==true end

local function hasFn(name) return type(android[name])=="function" end

local function pickFireMethod()
    for _,n in ipairs(FIRE_CANDIDATES) do
        if hasFn(n) then return n end
    end
    return nil
end

local function hand()
    local ok,n,c = pcall(android.getHandInfo,"main")
    if not ok or type(n)~="string" then return nil,0 end
    local l=n:lower()
    if l=="" or l=="air" or l=="minecraft:air" then return nil,0 end
    return n, tonumber(c) or 0
end

local function isAmmo(n)
    local l=tostring(n):lower()
    for _,w in ipairs(AMMO_WORDS) do
        if l:find(w,1,true) then return true end
    end
    return false
end

-- nombres visibles (getHandInfo puede devolver "Revolver" y no "cgs:revolver")
local ALIASES = {
    {"revolver","revólver","revolver"},
    {"shotgun","escopeta"},
    {"gatling"},
    {"nailgun","clavadora"},
    {"flintlock","pistola de chispa","pedernal"},
    {"blazegun"},
    {"launcher","lanzador"},
    {"frag_grenade","frag grenade","granada"},
    {"pneumatic","hammer","martillo"},
}

-- devuelve rango (1 = mejor) si es un arma CGS, o nil
local function weaponRank(n)
    if type(n)~="string" or isAmmo(n) then return nil end
    local l=n:lower()
    for rank,al in ipairs(ALIASES) do
        for _,a in ipairs(al) do
            if l:find(a,1,true) then return rank end
        end
    end
    return nil
end

local function hasItemId(n, id)
    if type(n)~="string" then return false end
    return n:lower():find(id:lower(),1,true)~=nil
end

local function isCgsWeaponName(n)
    if type(n)~="string" or isAmmo(n) then return false end
    if not hasItemId(n,CGS_PREFIX) then return false end
    for _,id in ipairs(CGS_WEAPON_IDS) do
        if hasItemId(n,id) then return true end
    end
    return false
end

local function isFuel(n)
    if type(n)~="string" then return false end
    local l=n:lower()
    if l:find("redstone_torch",1,true) or l:find("redstone torch",1,true) then return false end
    return l:find("redstone",1,true)~=nil
end

-- lo que lleva en la mano cuenta como arma si lo reconocemos,
-- o (ACCEPT_ANY_HELD) si no es munición ni combustible
local function isHeldWeapon(h)
    if not h then return false end
    if weaponRank(h) then return true end
    return ACCEPT_ANY_HELD and not isAmmo(h) and not isFuel(h)
end

local function isEmpty(n)
    if type(n)~="string" then return true end
    local l=n:lower()
    return l=="" or l=="empty" or l=="air" or l=="minecraft:air"
end

local function scanInventory()
    local inv={}
    for i=0,STASH_SIZE-1 do
        local ok,name,extra=pcall(android.getSlotInfo,i)
        if ok then
            if type(name)=="table" then name=name.name or name.id or name.item
            elseif type(name)=="boolean" and type(extra)=="string" then name=extra end
            inv[#inv+1]={slot=i,name=name,empty=isEmpty(name)}
        end
    end
    return inv
end

local function invSummary(inv)
    local by={}
    for _,it in ipairs(inv) do by[it.slot]=it.empty and "-" or tostring(it.name) end
    local out={}
    for i=0,STASH_SIZE-1 do out[#out+1]="r"..i.."="..tostring(by[i] or "?") end
    return table.concat(out," | ")
end

local function findFree(exclude)
    for _,it in ipairs(scanInventory()) do
        if it.slot~=exclude and it.empty then return it.slot end
    end
end

local function findFuel()
    for _,it in ipairs(scanInventory()) do
        if isFuel(it.name) then return it.slot end
    end
end

local function findWeapon()
    local inv=scanInventory()

    -- Primero cualquier arma CGS identificada por su namespace cgs:.
    -- Entre ellas se mantiene el orden: revolver, shotgun, gatling, etc.
    local cgsSlot,cgsRank
    for _,it in ipairs(inv) do
        if isCgsWeaponName(it.name) then
            local r=weaponRank(it.name) or 99
            if not cgsRank or r<cgsRank then cgsSlot,cgsRank=it.slot,r end
        end
    end
    if cgsSlot then return cgsSlot end

    -- Fallback para nombres visibles/localizados que no contienen cgs:.
    local bs,br
    for _,it in ipairs(inv) do
        local r=weaponRank(it.name)
        if r and (not br or r<br) then bs,br=it.slot,r end
    end
    return bs
end

-- ---------------------------------------------------------------- equipar
local function equipWeapon()
    local h=hand()
    if isHeldWeapon(h) then return true end
    local ws=findWeapon()
    if not ws then return false end

    local tmp
    if h then
        tmp=findFree(ws)
        if not tmp then return false end
        local ok,r=pcall(android.storeItem,tmp)
        if not ok or fail(r) then return false end
    end
    local ok,r=pcall(android.equipSlot,ws)
    if not ok or fail(r) then
        if tmp then pcall(android.equipSlot,tmp) end
        return false
    end
    local nw=hand()
    if isHeldWeapon(nw) then print("Arma: "..nw) return true end
    if nw then pcall(android.storeItem,ws) end
    if tmp then pcall(android.equipSlot,tmp) end
    return false
end

local function doRefuel()
    local held=hand()
    local inHand=held and isFuel(held)
    local fs_
    if not inHand then
        fs_=findFuel()
        if not fs_ then print("Refuel: sin redstone") return end
    end
    local tmp
    if not inHand and held then
        tmp=findFree(fs_)
        if not tmp then return end
        local ok,r=pcall(android.storeItem,tmp)
        if not ok or fail(r) then return end
    end
    if not inHand then
        local ok,r=pcall(android.equipSlot,fs_)
        if not ok or fail(r) then
            if tmp then pcall(android.equipSlot,tmp) end
            return
        end
        local n=hand()
        if not n or not isFuel(n) then
            if n then pcall(android.storeItem,fs_) end
            if tmp then pcall(android.equipSlot,tmp) end
            return
        end
    end
    local before=android.fuelLevel()
    pcall(android.refuel,1)
    local after=android.fuelLevel()
    if not inHand and hand() then pcall(android.storeItem,fs_) end
    if tmp then pcall(android.equipSlot,tmp) end
    print("Fuel: "..tostring(before).." -> "..tostring(after))
end

-- ---------------------------------------------------------------- mobs
local function dist(s,m)
    local dx,dy,dz=m.posX-s.posX,m.posY-s.posY,m.posZ-s.posZ
    return math.sqrt(dx*dx+dy*dy+dz*dz)
end

local function findMob()
    local s=android.getSelf()
    local best,bestType,bestD
    local ok,near=pcall(android.getNearbyMobs)
    if ok and type(near)=="table" then
        for _,m in pairs(near) do
            if type(m)=="table" and m.uuid and m.posX and m.posY and m.posZ then
                local label=tostring(m.name or m.type or ""):lower()
                for _,t in ipairs(TARGETS) do
                    local short=t:gsub("^[^:]+:","")
                    if label:find(t,1,true) or label:find(short,1,true) then
                        local d=dist(s,m)
                        if not bestD or d<bestD then best,bestType,bestD=m,t,d end
                        break
                    end
                end
            end
        end
    end
    return best,bestType
end

-- ---------------------------------------------------------------- disparo
local function fireOnce(mob)
    -- Disparo delegado a androidscompat + NTGL. No simular clics desde Lua.
    return true,"delegado a androidscompat/NTGL"
end

local lastErr=0
local function combat(mob)
    if not equipWeapon() then
        if os.clock()-lastErr>5 then
            print("Solo disparo: no hay arma CGS equipable. Mano="..tostring(hand() or "VACIA"))
            lastErr=os.clock()
        end
        return false
    end
    -- AndroidsCompatMod mantiene el objetivo y GunAttackGoal se encarga de
    -- acercarse, apuntar, disparar, recargar y controlar armas del avión.
    return true

end

-- ---------------------------------------------------------------- owners / chat
local function parseOwners(input)
    local o,names={},{}
    for p in tostring(input):gmatch("[^,\n]+") do
        local n=p:match("^%s*(.-)%s*$")
        if n~="" and not o[n:lower()] then o[n:lower()]=true names[#names+1]=n end
    end
    return o,names
end

local function loadOwners()
    if fs.exists(OWNERS_FILE) then
        local f=fs.open(OWNERS_FILE,"r")
        if not f then return nil end
        local o,n=parseOwners(f.readAll()) f.close()
        if #n==0 then return nil end
        return o
    end
    print("Jugadores (comas):")
    local o,n=parseOwners(read())
    while #n==0 do print("Escribe un nombre:") o,n=parseOwners(read()) end
    local f=fs.open(OWNERS_FILE,"w")
    if not f then return nil end
    f.write(table.concat(n,"\n").."\n") f.close()
    return o
end

local function waitStop()
    while true do
        local _,k=os.pullEvent("key")
        if k==keys.q then return end
    end
end

local function listen(unit,state,owners)
    local un=tostring(unit.name or "Android")
    while true do
        local _,d=os.pullEvent("onChatMessage")
        if type(d)=="table" and owners[tostring(d.senderName or ""):lower()] then
            local t,c,arg=tostring(d.message or ""):match("^!jarvis%s+(%S+)%s+(%S+)%s*(%S*)$")
            if t and (t:lower()=="all" or t:lower()==un:lower()) then
                c=c:lower()
                if c=="hold" then state.mode="hold" pcall(android.cancelTask)
                elseif c=="resume" or c=="patrol" then state.mode="patrol"
                elseif c=="return" then state.mode="return" pcall(android.cancelTask)
                elseif c=="fire" and arg~="" then
                    local valid=true
                    for p in arg:gmatch("[^+]+") do
                        if FORBIDDEN_FIRE_METHODS[p]
                            or (p~="cancel" and not ALLOWED_FIRE_METHODS[p])
                            or (p~="cancel" and not hasFn(p)) then
                            valid=false
                        end
                    end
                    if valid then
                        fireMethod=nil
                        print("Ignorado: el disparo lo controla androidscompat/NTGL")
                        pcall(android.sendChatMessage,"disparo=androidscompat/NTGL")
                    else
                        pcall(android.sendChatMessage,"Metodo bloqueado o inexistente: "..arg)
                    end
                elseif c=="test" then
                    state.testShot=true
                elseif c=="status" then
                    local s=android.getSelf()
                    pcall(android.sendChatMessage,un.."|"..state.mode
                        .."|HP="..tostring(s.health)
                        .."|fuel="..tostring(android.fuelLevel())
                        .."|wep="..tostring(hand() or "-")
                        .."|fire="..tostring(fireMethod))
                end
            end
        end
    end
end

-- ---------------------------------------------------------------- main
local function main()
    local s=android.getSelf()
    local home={x=s.posX,y=s.posY,z=s.posZ}
    local state={mode="patrol"}
    local owners=loadOwners()
    if not owners then print("Sin owners.") return end

    -- diagnostico: que puede hacer este androide
    local fns={}
    for k,v in pairs(android) do if type(v)=="function" then fns[#fns+1]=k end end
    table.sort(fns)
    print("android.* = "..table.concat(fns,", "))

    fireMethod=pickFireMethod()
    if fireMethod then
        print("Metodo NTGL: "..fireMethod)
    else
        print("Disparo: delegado a androidscompat -> NTGL GunAttackGoal")
        print("No uso attack/useEntity: ambos estan bloqueados")
    end
    print("Modo: solo disparo | armas CGS reconocidas: "..tostring(#CGS_WEAPON_IDS))
    print("Cambiar: !jarvis all fire <metodo NTGL> | test = prueba el disparo")
    print("Inventario: "..invSummary(scanInventory()))
    print("Pulsa Q para detener")
    equipWeapon()

    local points={
        {x=home.x+PATROL_RADIUS,y=home.y,z=home.z},
        {x=home.x,y=home.y,z=home.z+PATROL_RADIUS},
        {x=home.x-PATROL_RADIUS,y=home.y,z=home.z},
        {x=home.x,y=home.y,z=home.z-PATROL_RADIUS},
    }
    local np,lastRefuel,lastAlert=1,0,0

    parallel.waitForAny(
        function()
            while true do
                local self=android.getSelf()
                local task=android.currentTask()
                local now=os.clock()

                if self.health and self.health<=RETREAT_HEALTH and task~="idle" then
                    pcall(android.cancelTask)
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
                    inCombat=(mob~=nil)

                    local fuel=android.fuelLevel()
                    if fuel<REFUEL_AT and now-lastRefuel>=REFUEL_RETRY then
                        doRefuel() lastRefuel=now fuel=android.fuelLevel()
                    end

                    if state.testShot then
                        state.testShot=false
                        if mob then
                            if equipWeapon() then
                                if fireMethod then
                                    local ok,err=fireOnce(mob)
                                    print("TEST "..tostring(fireMethod)..": "..tostring(ok)..(ok and "" or (" "..tostring(err))))
                                else
                                    print("TEST: delegado a androidscompat/NTGL")
                                end
                            else
                                print("TEST: no hay arma CGS; no hago melee")
                            end
                        else
                            print("TEST: no hay mob cerca")
                        end
                    elseif fuel<1 then
                        -- sin combustible
                    elseif state.mode=="return" or (self.health and self.health<=RETREAT_HEALTH) then
                        lastTarget=nil
                        pcall(android.moveTo,home)
                    elseif state.mode=="patrol" then
                        if mob then
                            if lastTarget~=mob.uuid then
                                lastTarget=mob.uuid
                                if now-lastAlert>=ALERT_DELAY then
                                    pcall(android.sendChatMessage,"ALERTA: "..tostring(mobType))
                                    lastAlert=now
                                end
                            end
                            combat(mob)
                        else
                            lastTarget=nil
                            pcall(android.moveTo,points[np])
                            np=np%#points+1
                        end
                    end
                end
                sleep(SCAN_DELAY)
            end
        end,
        waitStop,
        function() listen(s,state,owners) end
    )
    pcall(android.cancelTask)
    print("Detenido.")
end

local ok,err=pcall(main)
if not ok then
    pcall(android.cancelTask)
    print("Error: "..tostring(err))
end
