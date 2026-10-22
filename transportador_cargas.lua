-- TRANSPORTADOR DE CARGAS AUTONOMO. No depende de rol_avion.lua.
local ROLE,LABEL="transport","TRANSPORTADOR DE CARGAS"
local RETRY,REFUEL_AT,REFUEL_RETRY=10,20,15
local OWNERS_FILE="owners.cfg"
local function owners()
 local r={}; if not fs.exists(OWNERS_FILE) then return r end
 local f=fs.open(OWNERS_FILE,"r"); if not f then return r end
 for n in f.readAll():gmatch("[^,\n]+") do n=n:match("^%s*(.-)%s*$"); if n~="" then r[n:lower()]=true end end
 f.close(); return r
end
local function send(s) pcall(android.sendChatMessage,s) end
local function idOf(unit) return tostring(unit.uuid or unit.UUID or "") end
local function autopilot(unit) send("!androidscompat autopilot "..ROLE.." "..idOf(unit)) end
local function order(unit,command,arg)
 send("!androidscompat order "..idOf(unit).." "..command..(arg~="" and (" "..arg) or ""))
end
local function main()
 local unit=android.getSelf(); local allowed=owners(); local nextPilot,nextFuel,nextStatus=-RETRY,-REFUEL_RETRY,0
 print(LABEL.." activo e independiente")
 print("Montaje autonomo | logistica militar | sin armas")
 parallel.waitForAny(function()
  while true do
   local now=os.clock()
   if now>=nextPilot then autopilot(unit); nextPilot=now+RETRY end
   if android.fuelLevel()<REFUEL_AT and now>=nextFuel then order(unit,"logistics",""); nextFuel=now+REFUEL_RETRY end
   if now>=nextStatus then print(LABEL.." | fuel="..tostring(android.fuelLevel())); nextStatus=now+15 end
   sleep(1)
  end
 end,function()
  while true do
   local event,data=os.pullEvent()
   if event=="key" and data==keys.q then return end
   if event=="onChatMessage" and type(data)=="table" and allowed[tostring(data.senderName or ""):lower()] then
    local target,command,arg=tostring(data.message or ""):match("^!avion%s+(%S+)%s+(%S+)%s*(%S*)")
    if target and (target:lower()=="all" or target:lower()==tostring(unit.name):lower()) then
     command=command:lower()
     if command=="mount" then autopilot(unit)
     elseif command=="capture" then send("!androidscompat capture "..idOf(unit))
     elseif command=="imprison" then send("!androidscompat imprison "..idOf(unit))
     elseif command=="release" then send("!androidscompat release "..idOf(unit))
     elseif command=="patrol" or command=="return" or command=="hold" or command=="follow" or command=="guard" or command=="rescue" or command=="logistics" then order(unit,command,arg)
     elseif command=="status" then send(LABEL.." | rol="..ROLE.." | fuel="..tostring(android.fuelLevel())) end
    end
   end
  end
 end)
 pcall(android.cancelTask); print(LABEL.." detenido.")
end
local ok,err=pcall(main); if not ok then pcall(android.cancelTask); print("Error: "..tostring(err)) end
