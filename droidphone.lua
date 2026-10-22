-- Android Mobile: instalador autocontenido para Pastebin.
-- No requiere HTTP ni descarga modulos externos.
local FILES = {
['startup.lua'] = [[local function drawCentered(y, text, foreground)
local width, height = term.getSize()
if y < 1 or y > height then return end
text = tostring(text)
if #text > width then text = text:sub(1, width) end
term.setCursorPos(math.max(1, math.floor((width - #text) / 2) + 1), y)
if type(term.isColor) ~= "function" or not term.isColor() then
foreground = colors.white
end
term.setTextColor(foreground or colors.white)
term.setBackgroundColor(colors.black)
term.write(text)
end
 
local function startupAnimation()
local width, height = term.getSize()
local top = math.max(1, math.floor((height - 12) / 2) + 1)
term.setBackgroundColor(colors.black)
term.setTextColor(colors.white)
term.clear()
 
drawCentered(top + 3, "ANDROID", colors.lime)
drawCentered(top + 4, "MOBILE", colors.white)
drawCentered(top + 5, "Classic 2.x launcher", colors.lightGray)
 
local barWidth = math.min(20, math.max(4, width - 4))
local barX = math.max(1, math.floor((width - barWidth) / 2) + 1)
local barY = top + 9
local innerWidth = math.max(1, barWidth - 2)
for frame = 0, 17 do
if barY >= 1 and barY <= height and barX + barWidth - 1 <= width then
term.setCursorPos(barX, barY)
term.setTextColor(colors.gray)
term.setBackgroundColor(colors.black)
term.write("[" .. string.rep(" ", innerWidth) .. "]")
 
local blockWidth = math.min(3, innerWidth)
local travel = math.max(0, innerWidth - blockWidth)
local blockX = barX + 1 + (frame % (travel + 1))
term.setCursorPos(blockX, barY)
term.setBackgroundColor(type(term.isColor) == "function" and term.isColor()
and colors.lime or colors.white)
term.write(string.rep(" ", blockWidth))
end
drawCentered(top + 11, "Iniciando...", colors.lightGray)
sleep(0.1)
end
 
term.setBackgroundColor(colors.black)
term.setTextColor(colors.white)
end
 
term.setBackgroundColor(colors.black)
term.setTextColor(colors.white)
term.clear()
term.setCursorPos(1, 1)
 
local ok, err = pcall(function()
startupAnimation()
require("sys.mobile.boot").run()
end)
 
if not ok then
term.setBackgroundColor(colors.black)
term.setTextColor(type(term.isColor) == "function" and term.isColor()
and colors.red or colors.white)
term.clear()
term.setCursorPos(1, 1)
print("Android Mobile: error de inicio")
print(tostring(err))
print("")
print("Comprueba que /sys/mobile y /apps")
print("estén instalados. Pulsa una tecla.")
os.pullEvent("key")
end
]],
['sys/mobile/boot.lua'] = [[local ui = require("sys.mobile.ui")
local sensors = require("sys.mobile.sensors")
local sound = require("sys.mobile.sound")
local unpackValues = table.unpack or unpack
 
local M = {}
local SETTINGS = "/sys/mobile/settings"
 
local function loadSettings()
local defaults = {
playerRange = 32,
createPeripheral = "",
createOutputs = { clutch = "back", gearbox = "left", sequencer = "right" },
createOutputMode = "redstone",
clickSounds = true,
bookmarks = {
{ "Wikipedia", "https://en.wikipedia.org" },
{ "DuckDuckGo", "https://lite.duckduckgo.com/lite/" },
{ "BBC News", "https://feeds.bbci.co.uk/news/rss.xml" },
},
}
if fs.exists(SETTINGS) then
local file = fs.open(SETTINGS, "r")
local raw = file and file.readAll()
if file then file.close() end
local ok, loaded = pcall(textutils.unserialize, raw or "")
if ok and type(loaded) == "table" then
for key, value in pairs(loaded) do defaults[key] = value end
end
end
return defaults
end
 
local function saveSettings(settings)
local file, err = fs.open(SETTINGS, "w")
if not file then return nil, err end
file.write(textutils.serialize(settings))
file.close()
return true
end
 
local function startApp(name, ctx)
local ok, app = pcall(require, "apps." .. name)
if not ok then
ctx.notify("No se pudo abrir " .. name .. ": " .. tostring(app))
return
end
ctx.app = app
ctx.appName = name
if app.open then
local good, err = pcall(app.open, ctx)
if not good then ctx.notify(tostring(err)) end
end
if ctx.playSound then ctx.playSound("action") end
end
 
local function toggleSplit(ctx)
if ctx.split then
if ctx.splitApp and ctx.splitApp.close then pcall(ctx.splitApp.close, ctx) end
ctx.split, ctx.splitApp = false, nil
if ctx.playSound then ctx.playSound("action") end
return
end
if not ctx.app then
ctx.notify("Abre una aplicacion antes de activar Split.")
return
end
local _, height = term.getSize()
if not window or height < 18 then
ctx.notify("Split requiere API window y al menos 18 filas.")
return
end
local moduleName = ctx.appName == "radar" and "home" or "radar"
local ok, app = pcall(require, "apps." .. moduleName)
if not ok then ctx.notify("No se pudo abrir panel dividido: " .. tostring(app)); return end
ctx.splitApp, ctx.split = app, true
if app.open then
local opened, err = pcall(app.open, ctx)
if not opened then ctx.notify("Split: " .. tostring(err)) end
end
if ctx.playSound then ctx.playSound("action") end
end
 
local function closeApp(ctx)
if ctx.app and ctx.app.close then pcall(ctx.app.close, ctx) end
if ctx.splitApp and ctx.splitApp.close then pcall(ctx.splitApp.close, ctx) end
ctx.app, ctx.appName, ctx.split, ctx.splitApp = nil, nil, false, nil
end
 
local function drawShutdownAnimation()
local width, height = term.getSize()
local centerY = math.max(1, math.floor(height / 2))
local barWidth = math.min(20, math.max(1, width - 4))
local barX = math.max(1, math.floor((width - barWidth) / 2) + 1)
local barY = math.min(height, centerY + 2)
local innerWidth = math.max(1, barWidth - 2)
 
term.setBackgroundColor(colors.black)
term.setTextColor(colors.white)
term.clear()
for frame = 0, 11 do
term.setBackgroundColor(colors.black)
term.setTextColor(colors.white)
term.clear()
ui.center(math.max(1, centerY - 2), "Android Mobile", colors.white, colors.black)
ui.center(centerY, "Apagando...", colors.lightGray, colors.black)
 
if barWidth >= 3 and barX + barWidth - 1 <= width then
term.setCursorPos(barX, barY)
term.setTextColor(ui.isBasicMode() and colors.white or colors.gray)
term.write("[" .. string.rep(" ", innerWidth) .. "]")
 
local remaining = math.max(0, innerWidth - math.floor(frame * innerWidth / 11))
if remaining > 0 then
term.setCursorPos(barX + 1, barY)
term.setBackgroundColor(ui.isBasicMode() and colors.white or colors.blue)
term.write(string.rep(" ", remaining))
end
end
sleep(0.08)
end
 
term.setBackgroundColor(colors.black)
term.setTextColor(ui.isBasicMode() and colors.white or colors.lightGray)
term.clear()
ui.center(centerY, "Ya puedes apagar el equipo.", colors.lightGray, colors.black)
sleep(0.35)
term.setBackgroundColor(colors.black)
term.clear()
end
 
local function shutdown(ctx)
closeApp(ctx)
sound.play("shutdown", ctx.settings.clickSounds)
drawShutdownAnimation()
os.shutdown()
end
 
local function resumeJobs(ctx, event)
for i = #ctx.jobs, 1, -1 do
local job = ctx.jobs[i]
local matches = job.waitFor == nil or event[1] == "timer" or (event[1] == job.waitFor
and (job.target == nil or event[2] == job.target))
if matches then
local ok, waitFor, target = coroutine.resume(job.thread)
if not ok then
table.remove(ctx.jobs, i)
if job.onError then pcall(job.onError, waitFor) end
ctx.notify("Tarea en segundo plano: " .. tostring(waitFor))
elseif coroutine.status(job.thread) == "dead" then
table.remove(ctx.jobs, i)
else
job.waitFor, job.target = waitFor, target
end
end
end
end
 
local function drawHome(ctx)
local home = require("apps.home")
home.draw(ctx)
end
 
local function render(ctx)
local parent = term.current()
local w, h = term.getSize()
term.setBackgroundColor(colors.black)
term.setTextColor(colors.white)
term.clear()
local canSplit = ctx.split and ctx.splitApp and window and h >= 18
if canSplit then
local paneHeight = math.floor((h - 2) / 2)
local topOk, top = pcall(window.create, parent, 1, 1, w, paneHeight + 1, true)
local bottomOk, bottom = pcall(window.create, parent, 1, paneHeight + 1, w, h - paneHeight, true)
if topOk and bottomOk then
term.redirect(top)
ui.title(ctx, ctx.app, 2)
local firstOk, firstErr = pcall(ctx.app.draw, ctx)
if not firstOk then ctx.notify("Split: " .. tostring(firstErr)) end
term.redirect(bottom)
ui.title(ctx, ctx.splitApp, 2)
local secondOk, secondErr = pcall(ctx.splitApp.draw, ctx)
if not secondOk then ctx.notify("Split: " .. tostring(secondErr)) end
term.redirect(parent)
else
term.redirect(parent)
ctx.notify("No se pudieron crear las ventanas divididas.")
end
elseif ctx.app then
ui.status(ctx)
ui.title(ctx, ctx.app)
local ok, err = pcall(ctx.app.draw, ctx)
if not ok then
ui.text(2, 4, "Error de aplicación:", colors.red, colors.black)
ui.text(2, 5, tostring(err), colors.red, colors.black, term.getSize())
end
else
ui.status(ctx)
drawHome(ctx)
end
if ctx.menu then
local w, h = term.getSize()
if ui.isBasicMode() then
local menuItems = { "Archivos", "Calculadora", "Notas", "Reloj", "Mapa", "Apagar" }
local x, y = 1, math.max(2, h - 8)
local panelWidth = math.min(w, 20)
ui.fill(x, y, panelWidth, math.min(8, h - y), colors.black)
ui.fill(x, y, panelWidth, 1, colors.white)
ui.text(x + 1, y, "BASIC MENU", colors.black, colors.white, panelWidth - 2)
for row, label in ipairs(menuItems) do
local rowY = y + row
if rowY < h then
local fg = row % 2 == 0 and colors.black or colors.white
local bg = row % 2 == 0 and colors.white or colors.black
ui.fill(x, rowY, panelWidth, 1, bg)
ui.text(x + 1, rowY, row .. ". " .. label, fg, bg, panelWidth - 2)
end
end
else
local x, y = 1, math.max(2, h - 8)
local panelWidth = math.min(w, 28)
local columnWidth = math.max(1, math.floor(panelWidth / 2))
ui.fill(x, y, panelWidth, 8, colors.lightGray)
ui.fill(x, y, panelWidth, 1, colors.blue)
ui.text(x + 1, y, "Programas", colors.white, colors.blue, panelWidth - 2)
if x + panelWidth <= w then
ui.fill(x + panelWidth, y + 1, 1, 7, colors.gray)
end
if y + 8 < h then
ui.fill(x + 1, y + 8, panelWidth, 1, colors.gray)
end
local pages = {
{
{ "IE Mobile", "Radar", "Messenger", "Create", "GPS" },
{ "Archivos", "Ajustes", "Pocket CMD", "Geo Scanner", "Split", "Mapa" },
},
{
{ "Calculadora", "Notas", "Reloj", "Redstone", "Inventario" },
{ "Media", "Inicio", "Apagar", "YouTube", "Música" },
},
}
local entries = pages[ctx.menuPage or 1]
for row = 1, 6 do
local rowY = y + row
for column = 1, 2 do
local label = entries[column][row]
if label and label ~= "" then
local cellX = x + (column - 1) * columnWidth
local color = row % 2 == 0 and colors.white or colors.lightGray
ui.fill(cellX, rowY, math.min(columnWidth, panelWidth - (column - 1) * columnWidth), 1, color)
ui.text(cellX + 1, rowY, label, colors.blue, color, math.max(1, columnWidth - 2))
end
end
end
local footerY = y + 7
ui.text(x + 1, footerY, ctx.menuPage == 2 and "< Apps 2/2" or "Apps 1/2 >",
colors.blue, colors.lightGray, panelWidth - 2)
end
end
term.redirect(parent)
ui.status(ctx)
ui.dock(ctx)
end
 
local function isTouch(event, x, y, ctx)
local w, h = term.getSize()
local slot = math.max(1, math.floor(w / 4))
if ctx.menu and ui.isBasicMode() then
if y == h then
ctx.menu = false
return true
end
local menuTop = math.max(2, h - 8)
local row = y - menuTop
local actions = { "explorer", "calculator", "notes", "clock", "map", "shutdown" }
if x <= math.min(w, 20) and row >= 1 and row <= #actions and y < h then
ctx.menu = false
if actions[row] == "shutdown" then shutdown(ctx)
else startApp(actions[row], ctx) end
else
ctx.menu = false
end
return true
end
if ctx.menu then
if y == h then
ctx.menu = false
return true
end
local menuTop = math.max(2, h - 8)
if x >= 1 and x <= math.min(w, 28) and y == menuTop + 7 then
ctx.menuPage = ctx.menuPage == 2 and 1 or 2
return true
end
if x >= 1 and x <= math.min(w, 28) and y > menuTop and y <= menuTop + 6 then
local columnWidth = math.max(1, math.floor(math.min(w, 28) / 2))
local column = x > columnWidth and 2 or 1
local row = y - menuTop
local pages = {
{
{ "ie", "radar", "messenger", "create", "gps" },
{ "explorer", "settings", "cmd", "ores", "split", "map" },
},
{
{ "calculator", "notes", "clock", "redstone", "inventory" },
{ "media", "home", "shutdown", "youtube", "music" },
},
}
local action = pages[ctx.menuPage or 1][column][row]
ctx.menu = false
if action == "split" then
toggleSplit(ctx)
elseif action and action ~= "" and action ~= "home" and action ~= "shutdown" then
startApp(action, ctx)
elseif action == "home" then
closeApp(ctx)
elseif action == "shutdown" then
shutdown(ctx)
end
return true
end
ctx.menu = false
end
if y == h then
if not ctx.app then
if ui.isBasicMode() then
if x <= slot then startApp("explorer", ctx)
elseif x <= slot * 2 then ctx.menu = not ctx.menu
elseif x <= slot * 3 then startApp("notes", ctx)
else startApp("clock", ctx) end
elseif x <= slot then startApp("gps", ctx)
elseif x <= slot * 2 then ctx.menu = not ctx.menu
elseif x <= slot * 3 then startApp("messenger", ctx)
else startApp("ie", ctx) end
return true
elseif x <= slot then
ctx.menu = not ctx.menu
return true
end
if ctx.app then
if x <= slot * 2 then
if ctx.app.back then pcall(ctx.app.back, ctx) else closeApp(ctx) end
return true
end
if x <= slot * 3 then ctx.menu = not ctx.menu; return true end
closeApp(ctx)
return true
end
end
if ctx.app and y == 2 and x >= term.getSize() - 3 then
closeApp(ctx)
return true
end
if ctx.split and ctx.splitApp and y == math.floor((h - 2) / 2) + 2
and x >= term.getSize() - 3 then
toggleSplit(ctx)
return true
end
if not ctx.app then
local home = require("apps.home")
home.touch(ctx, x, y, startApp)
return true
end
local app, localY = ctx.app, y
if ctx.split and ctx.splitApp and h >= 18 then
local paneHeight = math.floor((h - 2) / 2)
if y >= paneHeight + 2 then
app, localY = ctx.splitApp, y - paneHeight
elseif y < 2 or y > paneHeight + 1 then
return true
end
end
if app.touch then
local ok, err
if app == ctx.splitApp and app.title == "Inicio" then
ok, err = pcall(app.touch, ctx, x, localY, startApp)
else
ok, err = pcall(app.touch, ctx, x, localY)
end
if not ok then ctx.notify("Error: " .. tostring(err)) end
end
return true
end
 
function M.run()
local hasColor = type(term.isColor) == "function" and term.isColor()
ui.setBasicMode(not hasColor)
local settings = loadSettings()
sound.play("startup", settings.clickSounds)
local ctx
ctx = {
settings = settings,
app = nil,
appName = nil,
menu = false,
notifications = {},
jobs = {},
world = hasColor and sensors.world() or {},
power = hasColor and sensors.power() or "--",
spawn = function(fn, onError)
local thread = coroutine.create(fn)
local job = { thread = thread, onError = onError }
ctx.jobs[#ctx.jobs + 1] = job
local ok, waitFor, target = coroutine.resume(thread)
if not ok then
table.remove(ctx.jobs)
if onError then pcall(onError, waitFor) end
ctx.notify("Tarea en segundo plano: " .. tostring(waitFor))
elseif coroutine.status(thread) == "dead" then
table.remove(ctx.jobs)
else
job.waitFor, job.target = waitFor, target
end
end,
notify = function(message)
message = tostring(message)
ctx.notifications[#ctx.notifications + 1] = message
while #ctx.notifications > 8 do table.remove(ctx.notifications, 1) end
if message:lower():find("overstress", 1, true) then
local speaker = require("sys.mobile.peripherals").find("speaker")
if speaker and speaker.playNote then pcall(speaker.playNote, "pling", 1, 12) end
end
end,
saveSettings = function()
local ok, err = saveSettings(settings)
if not ok then error("No se guardó la configuración: " .. tostring(err)) end
end,
playSound = function(kind)
return sound.play(kind, settings.clickSounds)
end,
}
local tick = os.startTimer(1)
local lastAlert = false
local monitor
if peripheral and peripheral.getNames and peripheral.wrap then
for _, name in ipairs(peripheral.getNames()) do
local typeOk, typeName = pcall(peripheral.getType, name)
if typeOk and typeName == "monitor" then
local ok, candidate = pcall(peripheral.wrap, name)
if ok and candidate then
local sizeOk, width, height = pcall(candidate.getSize)
if sizeOk and width <= height then
monitor = candidate
break
end
end
end
end
end
if monitor then
pcall(monitor.setTextScale, 0.5)
term.redirect(monitor)
end
while true do
render(ctx)
local event = { os.pullEvent() }
if event[1] == "monitor_touch" then
event = { "mouse_click", 1, event[3], event[4] }
end
if event[1] == "timer" and event[2] == tick then
if hasColor then
ctx.world = sensors.world()
ctx.power = sensors.power()
end
if ctx.appName == "radar" and ctx.app.refresh then
pcall(ctx.app.refresh, ctx)
elseif ctx.appName == "create" and ctx.app.refresh then
pcall(ctx.app.refresh, ctx)
end
if ctx.app and ctx.app.tick then
local ticked, tickError = pcall(ctx.app.tick, ctx)
if not ticked then ctx.notify("Error de app: " .. tostring(tickError)) end
end
if hasColor then
local telemetry = sensors.createTelemetry(settings)
if telemetry and telemetry.overstressed and not lastAlert then
ctx.notify("Create Overstress: red detenida")
end
lastAlert = telemetry and telemetry.overstressed or false
end
tick = os.startTimer(1)
elseif event[1] == "mouse_click" then
ctx.playSound("click")
isTouch(event[2], event[3], event[4], ctx)
elseif event[1] == "mouse_drag" and ctx.app and ctx.app.drag then
pcall(ctx.app.drag, ctx, event[2], event[3], event[4])
elseif event[1] == "chat" or event[1] == "chat_message" then
local user, message = event[2], event[3]
ctx.notify(tostring(user or "Servidor") .. ": " .. tostring(message or ""))
if ctx.app and ctx.app.event then
local ok, err = pcall(ctx.app.event, ctx, event[1], user, message)
if not ok then ctx.notify("Error: " .. tostring(err)) end
end
elseif ctx.app and ctx.app.event then
local ok, err = pcall(ctx.app.event, ctx, unpackValues(event))
if not ok then ctx.notify("Error: " .. tostring(err)) end
end
resumeJobs(ctx, event)
end
end
 
return M
]],
['sys/mobile/ui.lua'] = [[local M = {}
local C = colors
local basicMode = false
 
M.colors = {
blue = C.lightBlue, title = C.gray, navy = C.black,
light = C.white, panel = C.gray, text = C.white,
green = C.lime, gray = C.gray, red = C.red,
}
 
M.theme = {
sky = C.gray,
deepBlue = C.black,
paleBlue = C.lightGray,
grass = C.lime,
leaf = C.green,
panel = C.black,
tile = C.gray,
ink = C.white,
muted = C.lightGray,
highlight = C.white,
}
 
local function backgroundColor(color)
if color == C.white then return C.black end
if color == C.lightGray or color == C.lightBlue then return C.gray end
if color == C.cyan then return C.blue end
if color == C.green then return C.lime end
return color
end
 
local function basicColor(color)
if color == C.white or color == C.lightGray or color == C.lightBlue
or color == C.cyan or color == C.lime or color == C.yellow
or color == C.orange or color == C.pink then
return C.white
end
return C.black
end
 
function M.setBasicMode(enabled)
basicMode = enabled == true
end
 
function M.isBasicMode()
return basicMode
end
 
function M.size()
return term.getSize()
end
 
function M.fill(x, y, width, height, color, char)
local w, h = term.getSize()
local line = string.rep(char or " ", math.max(0, math.min(width, w)))
term.setBackgroundColor(basicMode and basicColor(color or C.black)
or backgroundColor(color or C.black))
for row = math.max(1, y), math.min(h, y + height - 1) do
term.setCursorPos(math.max(1, x), row)
term.write(line:sub(1, math.max(0, w - x + 1)))
end
end
 
function M.text(x, y, value, fg, bg, maxWidth)
local w, h = term.getSize()
if y < 1 or y > h or x > w then return end
value = tostring(value or "")
if maxWidth then value = value:sub(1, maxWidth) end
value = value:sub(1, math.max(0, w - x + 1))
term.setCursorPos(math.max(1, x), y)
if fg then
local surface = bg and (basicMode and basicColor(bg) or backgroundColor(bg))
if basicMode then
if bg then
fg = surface == C.white and C.black or C.white
else
fg = basicColor(fg)
end
elseif not bg then
if fg == C.black or fg == C.blue then fg = C.white
elseif fg == C.gray then fg = C.lightGray end
elseif surface == C.black or surface == C.gray then
if fg == C.black or fg == C.blue then fg = C.white
elseif fg == C.gray then fg = C.lightGray end
elseif surface == C.blue and (fg == C.black or fg == C.blue) then
fg = C.white
elseif fg == C.white and (surface == C.lime or surface == C.yellow
or surface == C.orange) then
fg = C.black
end
term.setTextColor(fg)
end
if bg then
term.setBackgroundColor(basicMode and basicColor(bg) or backgroundColor(bg))
end
term.write(value)
end
 
function M.center(y, value, fg, bg)
local w = term.getSize()
value = tostring(value or "")
M.text(math.max(1, math.floor((w - #value) / 2) + 1), y, value, fg, bg)
end
 
function M.button(x, y, width, label, bg, fg)
local w = term.getSize()
width = math.max(0, math.min(width, w - x + 1))
if width == 0 then return { x = x, y = y, width = 0, height = 1 } end
label = tostring(label)
bg = bg or C.gray
M.fill(x, y, width, 1, bg)
local caption = label:sub(1, width)
local left = x + math.max(0, math.floor((width - #caption) / 2))
M.text(left, y, caption, fg or C.white, bg, width)
return { x = x, y = y, width = width, height = 1 }
end
 
function M.hit(rect, x, y)
return rect and x >= rect.x and x < rect.x + rect.width
and y >= rect.y and y < rect.y + rect.height
end
 
function M.wrap(value, width)
local lines = {}
width = math.max(1, width)
for paragraph in (tostring(value or "") .. "\n"):gmatch("(.-)\n") do
paragraph = paragraph:gsub("%s+", " ")
if paragraph == "" then
lines[#lines + 1] = ""
else
while #paragraph > width do
local cut = paragraph:sub(1, width):match("^.*()%s")
if not cut or cut < 2 then cut = width end
lines[#lines + 1] = paragraph:sub(1, cut):gsub("%s+$", "")
paragraph = paragraph:sub(cut + 1):gsub("^%s+", "")
end
lines[#lines + 1] = paragraph
end
end
return lines
end
 
function M.status(ctx)
local w = term.getSize()
M.fill(1, 1, w, 1, M.theme.deepBlue)
if basicMode then
local notice = #ctx.notifications > 0 and "!" or ""
M.text(1, 1, " BASIC", C.white, C.black, math.min(7, w))
local indicators = notice .. " " .. os.date("%H:%M")
M.text(math.max(1, w - #indicators + 1), 1, indicators, C.white, C.black, #indicators)
return
end
local signal = http and http.get and "W" or "X"
local power = ctx.power or "--"
local time = os.date("%H:%M")
local notice = #ctx.notifications > 0 and "!" or " "
M.text(1, 1, " A", C.lime, M.theme.deepBlue, math.min(3, w))
local indicators = signal .. " " .. power .. " " .. notice .. " " .. time
M.text(math.max(1, w - #indicators + 1), 1, indicators, C.white, M.theme.deepBlue, #indicators)
end
 
function M.title(ctx, app, row)
local w = term.getSize()
row = row or 2
M.fill(1, row, w, 1, M.theme.sky)
M.text(2, row, app.title or app.name or "Aplicacion", C.white, M.theme.sky, math.max(1, w - 6))
M.fill(math.max(1, w - 2), row, math.min(3, w), 1, C.red)
M.text(w - 1, row, "X", C.white, C.red, 1)
end
 
function M.dock(ctx)
local w, h = term.getSize()
local slot = math.max(1, math.floor(w / 4))
M.fill(1, h, w, 1, M.theme.deepBlue)
if not ctx.app then
if basicMode then
M.button(1, h, slot, w < 20 and "File" or "Files", C.gray, C.white)
M.button(slot + 1, h, slot, "Apps", C.lightGray, C.black)
M.button(slot * 2 + 1, h, slot, w < 20 and "Note" or "Notes", C.gray, C.white)
M.button(slot * 3 + 1, h, w - slot * 3, w < 20 and "Time" or "Clock", C.gray, C.white)
else
M.button(1, h, slot, "GPS", C.gray, C.white)
M.button(slot + 1, h, slot, "Apps", C.lightGray, C.black)
M.button(slot * 2 + 1, h, slot, "Chat", C.gray, C.white)
M.button(slot * 3 + 1, h, w - slot * 3, "Web", C.gray, C.white)
end
else
M.button(1, h, slot, w < 32 and "Menu" or "Menu", C.gray, C.white)
local backLabel = w < 32 and "<" or "Atras"
local menuLabel = w < 32 and "Menu" or "Menu"
local closeLabel = w < 32 and "X" or "Cerrar"
M.button(slot + 1, h, slot, backLabel, M.theme.sky, C.white)
M.button(slot * 2 + 1, h, slot, menuLabel, M.theme.sky, C.white)
M.button(slot * 3 + 1, h, w - slot * 3, closeLabel, M.theme.sky, C.white)
end
end
 
return M
]],
['sys/mobile/lib.lua'] = [[local ui = require("sys.mobile.ui")
local M = { ui = ui }
 
function M.label(ctx, x, y, text, width)
ui.text(x, y, text, colors.black, colors.white, width)
end
 
function M.heading(ctx, y, title)
local w = term.getSize()
ui.fill(1, y, w, 1, colors.lightBlue)
ui.text(2, y, title, colors.white, colors.lightBlue, w - 2)
end
 
function M.notification(ctx, message)
ctx.notify(message)
end
 
function M.prompt(ctx, caption, initial)
term.setBackgroundColor(colors.black)
term.setTextColor(colors.white)
local _, height = term.getSize()
term.setCursorPos(1, math.max(1, height - 1))
term.clearLine()
write(caption .. " ")
return read(nil, nil, nil, initial or "")
end
 
function M.readFile(path, limit)
if not fs.exists(path) or fs.isDir(path) then return nil, "No se puede leer el archivo" end
local handle, err = fs.open(path, "r")
if not handle then return nil, err end
local data = handle.read(limit or 32768)
handle.close()
return data or ""
end
 
function M.writeFile(path, data)
local parent = fs.getDir(path)
if parent ~= "" and not fs.exists(parent) then fs.makeDir(parent) end
local handle, err = fs.open(path, "w")
if not handle then return nil, err end
handle.write(data)
handle.close()
return true
end
 
return M
]],
['sys/mobile/peripherals.lua'] = [[local M = {}
 
local function invoke(device, method, ...)
if not device or type(device[method]) ~= "function" then
return nil
end
local ok, value = pcall(device[method], ...)
if ok then return value end
return nil
end
 
function M.find(typeName)
if not peripheral or not peripheral.find then return nil end
local ok, device = pcall(peripheral.find, typeName)
if ok then return device end
return nil
end
 
function M.method(device, names, ...)
for _, name in ipairs(names) do
local value = invoke(device, name, ...)
if value ~= nil then return value, name end
end
return nil
end
 
function M.describe(device)
local result = {}
if not device then return result end
local ok, methods = pcall(peripheral.getMethods, peripheral.getName(device))
if ok and type(methods) == "table" then
for _, name in ipairs(methods) do result[name] = true end
end
return result
end
 
function M.names()
if not peripheral or not peripheral.getNames then return {} end
local ok, names = pcall(peripheral.getNames)
if ok and type(names) == "table" then return names end
return {}
end
 
return M
]],
['sys/mobile/sensors.lua'] = [[local devices = require("sys.mobile.peripherals")
local M = {}
 
local function text(value)
if value == nil then return nil end
if type(value) == "table" then return text(value.name or value.biome or value.weather or value.value) end
return tostring(value)
end
 
function M.world()
local environment = devices.find("environmentDetector")
local weather = devices.find("weatherDetector")
local info = {
biome = devices.method(environment, { "getBiome" }),
dimension = devices.method(environment, { "getDimension" }),
moon = devices.method(environment, { "getMoonId", "getMoonPhase" }),
worldTime = devices.method(environment, { "getTime", "getWorldTime", "getDay" }),
weather = devices.method(weather, { "getWeather" }),
}
for key, value in pairs(info) do info[key] = text(value) or "--" end
for _, key in ipairs({ "biome", "dimension", "moon", "worldTime", "weather" }) do
if info[key] == nil then info[key] = "--" end
end
return info
end
 
function M.players(range)
local detector = devices.find("playerDetector")
if not detector then return nil, "Player Detector no conectado" end
local names = devices.method(detector, { "getPlayersInRange", "getPlayers" }, range or 32)
if type(names) ~= "table" then return nil, "El detector no devolvió jugadores" end
local result = {}
for _, entry in ipairs(names) do
local name = type(entry) == "table" and (entry.name or entry.username) or entry
if type(name) == "string" then
local position = devices.method(detector, { "getPlayerPos", "getPlayerPosition" }, name)
if type(position) ~= "table" and type(entry) == "table" then position = entry end
if type(position) ~= "table" then position = {} end
local x, y, z = position.x or "?", position.y or "?", position.z or "?"
local distance = position.distance or "?"
result[#result + 1] = {
name = name,
x = x, y = y, z = z, distance = distance,
}
end
end
return result
end
 
function M.createTelemetry(config)
local configured = config.createPeripheral
local device
if configured and configured ~= "" and peripheral then
local ok, value = pcall(peripheral.wrap, configured)
if ok then device = value end
end
if not device then
for _, name in ipairs(devices.names()) do
local ok, candidate = pcall(peripheral.wrap, name)
if ok and candidate then
local methods = devices.describe(candidate)
if methods.getSpeed or methods.getStress or methods.getStressCapacity then
device = candidate
break
end
end
end
end
if not device then return nil, "No se detectó un puente/periférico de Create" end
local rpm = devices.method(device, { "getSpeed", "getRPM", "getRotationSpeed" })
local stress = devices.method(device, { "getStress", "getStressImpact" })
local capacity = devices.method(device, { "getStressCapacity", "getCapacity" })
local stalled = devices.method(device, { "isStalled", "getStalled" })
local overstressed = devices.method(device, { "isOverstressed", "getOverstressed" })
return {
rpm = rpm or 0,
stress = stress or 0,
capacity = capacity,
stalled = stalled == true,
overstressed = overstressed == true,
}
end
 
function M.power()
local storage = devices.find("energyStorage")
if storage then
local energy = devices.method(storage, { "getEnergy", "getEnergyStored" })
local capacity = devices.method(storage, { "getEnergyCapacity", "getMaxEnergy" })
if type(energy) == "number" and type(capacity) == "number" and capacity > 0 then
return math.floor(energy * 100 / capacity) .. "%"
end
end
if redstone and redstone.getInput then
for _, side in ipairs({ "left", "right", "top", "bottom", "front", "back" }) do
if redstone.getInput(side) then return "R+" end
end
return "R-"
end
return "--"
end
 
return M
]],
['sys/mobile/sound.lua'] = [[local M = {}
local speaker
 
local function getSpeaker()
if speaker then return speaker end
if not peripheral or type(peripheral.find) ~= "function" then return nil end
local ok, found = pcall(peripheral.find, "speaker")
if ok then speaker = found end
return speaker
end
 
function M.play(kind, enabled)
if enabled == false then return false end
local device = getSpeaker()
if not device or type(device.playNote) ~= "function" then return false end
 
local notes
if kind == "action" then
notes = {
{ "chime", 0.65, 17 },
{ "pling", 0.45, 21 },
}
elseif kind == "startup" then
notes = {
{ "chime", 0.65, 12 },
{ "bell", 0.65, 16 },
{ "pling", 0.6, 19 },
{ "chime", 0.55, 21 },
{ "pling", 0.45, 24 },
}
elseif kind == "shutdown" then
notes = {
{ "pling", 0.55, 22 },
{ "bell", 0.6, 19 },
{ "chime", 0.55, 16 },
{ "bell", 0.45, 12 },
{ "chime", 0.35, 9 },
}
else
notes = { { "hat", 0.35, 18 } }
end
 
local played = false
for index, note in ipairs(notes) do
local ok = pcall(device.playNote, note[1], note[2], note[3])
played = played or ok
if index < #notes and (kind == "startup" or kind == "shutdown") then
sleep(0.1)
end
end
return played
end
 
return M
]],
['drivers/create.lua'] = [[local peripherals = require("sys.mobile.peripherals")
local M = {}
local KEYS = { "clutch", "gearbox", "sequencer" }
 
function M.readOutputs(settings)
local result = {}
local integrator
if settings.createOutputMode == "integrator" then
integrator = peripherals.find("redstoneIntegrator")
end
for _, key in ipairs(KEYS) do
local side = settings.createOutputs and settings.createOutputs[key]
local value
if side and integrator and type(integrator.getOutput) == "function" then
local ok, output = pcall(integrator.getOutput, side)
if ok then value = output end
elseif side and redstone and redstone.getOutput then
local ok, output = pcall(redstone.getOutput, side)
if ok then value = output end
end
result[key] = value == true
end
return result
end
 
function M.setOutput(settings, key, value)
local side = settings.createOutputs and settings.createOutputs[key]
if not side or side == "" then error("Configura una salida para " .. key) end
if settings.createOutputMode == "integrator" then
local device = peripherals.find("redstoneIntegrator")
if not device or type(device.setOutput) ~= "function" then
error("Redstone Integrator no conectado")
end
device.setOutput(side, value)
else
if not redstone or not redstone.setOutput then error("API de redstone no disponible") end
redstone.setOutput(side, value)
end
end
 
return M
]],
['apps/home.lua'] = [[local ui = require("sys.mobile.ui")
local M = { title = "Inicio" }
 
local apps = {
{ "Mi PC", "explorer", "PC" },
{ "Internet", "ie", "IE" },
{ "Create", "create", "CR" },
{ "Sensores", "radar", "AP" },
{ "Media", "media", "WMP" },
{ "Ajustes", "settings", "SYS" },
{ "Messenger", "messenger", "MSN" },
{ "Pocket CMD", "cmd", ">" },
{ "GPS", "gps", "GPS" },
{ "Ores", "ores", "ORE" },
{ "Calculadora", "calculator", "CALC" },
{ "Notas", "notes", "TXT" },
{ "Reloj", "clock", "TIME" },
{ "Redstone", "redstone", "RS" },
{ "Inventario", "inventory", "INV" },
{ "YouTube", "youtube", "YT" },
{ "Música", "music", "MUS" },
{ "Mapa", "map", "MAP" },
}
 
local iconColors = {
colors.cyan, colors.blue, colors.orange, colors.cyan,
colors.purple, colors.green, colors.red, colors.gray,
colors.cyan, colors.orange, colors.purple, colors.gray, colors.blue,
colors.red, colors.orange, colors.red, colors.green,
}
 
local page = 1
local basicApps = {
{ "Files", "explorer", "FILE" },
{ "Calc", "calculator", "123" },
{ "Notes", "notes", "TXT" },
{ "Clock", "clock", "TIME" },
{ "Map", "map", "MAP" },
}
 
local function visibleApps()
return ui.isBasicMode() and basicApps or apps
end
 
local function compact(text, width)
text = tostring(text or "")
if #text > width then return text:sub(1, math.max(1, width - 1)) .. "~" end
return text
end
 
local function centered(x, y, width, value, fg, bg)
value = compact(value, width)
local left = x + math.max(0, math.floor((width - #value) / 2))
ui.text(left, y, value, fg, bg, width)
end
 
local function dimensionName(world)
local dimension = tostring(world.dimension or "--")
local names = {
["minecraft:overworld"] = "Overworld",
["minecraft:the_nether"] = "Nether",
["minecraft:the_end"] = "The End",
}
return names[dimension] or dimension
end
 
local function weatherLine(world, width)
local place = tostring(world.biome or "--")
local weather = tostring(world.weather or "--")
return compact(place .. " / " .. weather .. " / " .. dimensionName(world), width)
end
 
local digitGlyphs = {
["0"] = { " _ ", "| |", "|_|" },
["1"] = { " ", " |", " |" },
["2"] = { " _ ", " _|", "|_ " },
["3"] = { " _ ", " _|", " _|" },
["4"] = { " ", "|_|", " |" },
["5"] = { " _ ", "|_ ", " _|" },
["6"] = { " _ ", "|_ ", "|_|" },
["7"] = { " _ ", " |", " |" },
["8"] = { " _ ", "|_|", "|_|" },
["9"] = { " _ ", "|_|", " _|" },
[":"] = { " ", ".", "." },
}
 
local function drawClock(w)
local clock = os.date("%H:%M")
local width = #clock * 4 - 1
if w < width + 2 then
centered(2, 3, w - 2, clock, colors.white, colors.gray)
return
end
local x = math.floor((w - width) / 2) + 1
for index = 1, #clock do
local glyph = digitGlyphs[clock:sub(index, index)]
local offset = x + (index - 1) * 4
for row = 1, 3 do
ui.text(offset, row + 1, glyph[row], colors.white, colors.gray, 3)
end
end
end
 
function M.draw(ctx)
local w, h = term.getSize()
local world = ctx.world or {}
local visible = visibleApps()
 
ui.fill(1, 2, w, math.max(1, h - 2), colors.black)
for y = 2, h - 2 do
for x = 1, w do
if (x * 7 + y * 11) % 29 == 0 then
ui.text(x, y, ".", colors.gray, colors.black, 1)
end
end
end
 
ui.fill(2, 2, math.max(1, w - 2), 3, colors.gray)
drawClock(w)
local subtitle = ui.isBasicMode() and "Basic mode / offline" or
os.date("%a, %d %b") .. " " .. weatherLine(world, w - 2)
ui.text(2, 5, compact(subtitle, w - 2), colors.white, colors.black, w - 2)
 
local columns
if ui.isBasicMode() then
columns = w < 22 and 2 or 3
else
columns = w >= 34 and 4 or (w < 22 and 2 or 3)
end
local rows = math.max(1, math.min(4, math.floor((h - 7) / 3)))
local cell = math.max(1, math.floor((w - 2) / columns))
local perPage = columns * rows
local pages = math.max(1, math.ceil(#visible / perPage))
page = math.min(page, pages)
local first = (page - 1) * perPage + 1
local last = math.min(#visible, first + perPage - 1)
for i = first, last do
local entry = visible[i]
local localIndex = i - first
local column = localIndex % columns
local row = math.floor(localIndex / columns)
local x = 2 + column * cell
local y = 6 + row * 3
if y < h - 2 then
local tileWidth = math.max(1, math.min(cell - 1, w - x + 1))
ui.fill(x, y, tileWidth, 2, colors.black)
local iconColor = ui.isBasicMode() and (i % 2 == 0 and colors.white or colors.black)
or iconColors[i]
ui.fill(x, y, tileWidth, 1, iconColor)
centered(x, y, tileWidth, "[" .. entry[3] .. "]",
iconColor == colors.white and colors.black or colors.white, iconColor)
centered(x, y + 1, tileWidth, entry[1], colors.white, colors.black)
end
end
 
if pages > 1 and h >= 12 then
ui.fill(1, h - 2, w, 1, colors.black)
ui.text(2, h - 2, "<", colors.white, colors.gray, 1)
ui.text(math.max(1, math.floor((w - 5) / 2)), h - 2,
"o " .. page .. "/" .. pages .. " o", colors.white, colors.black, 12)
ui.text(w, h - 2, ">", colors.white, colors.gray, 1)
elseif #ctx.notifications > 0 and h >= 12 then
ui.fill(2, h - 2, math.max(1, w - 2), 1, colors.gray)
ui.text(3, h - 2, "!" .. compact(ctx.notifications[#ctx.notifications], w - 4),
colors.white, colors.gray, w - 4)
end
end
 
function M.touch(ctx, x, y, startApp)
local w = term.getSize()
local _, h = term.getSize()
local visible = visibleApps()
local columns
if ui.isBasicMode() then
columns = w < 22 and 2 or 3
else
columns = w >= 34 and 4 or (w < 22 and 2 or 3)
end
local rows = math.max(1, math.min(4, math.floor((h - 7) / 3)))
local perPage = columns * rows
local pages = math.max(1, math.ceil(#visible / perPage))
if pages > 1 and y == h - 2 then
if x <= 3 then page = page > 1 and page - 1 or pages
elseif x >= w - 2 then page = page < pages and page + 1 or 1 end
return
end
if y < 6 then return end
local cell = math.max(1, math.floor((w - 2) / columns))
local col = math.floor((x - 2) / cell)
local row = math.floor((y - 6) / 3)
if col < 0 or col >= columns or row < 0 or x > w - 1 then return end
local index = (page - 1) * perPage + row * columns + col + 1
if visible[index] and y <= h - 3 then
startApp(visible[index][2], ctx)
end
end
 
return M
]],
['apps/music.lua'] = [[local ui = require("sys.mobile.ui")
local peripherals = require("sys.mobile.peripherals")
local lib = require("sys.mobile.lib")
local M = { title = "Streaming Music" }
 
local API = "https://ipod-2to6magyna-uc.a.run.app/"
local VERSION = "2.1"
local state = { query = "", results = {}, selected = 1, page = 1,
message = "Busca una canción o pega un enlace de YouTube.", searching = false,
playing = false, stop = false, speaker = nil, speakerName = nil, now = nil,
searchUrl = nil, downloadUrl = nil }
 
local function compact(value, width)
value = tostring(value or ""):gsub("[%c]", " ")
if #value > width then return value:sub(1, math.max(1, width - 1)) .. "~" end
return value
end
 
local function stopPlayback()
state.stop = true
if state.speaker and state.speaker.stop then pcall(state.speaker.stop) end
if state.speakerName then os.queueEvent("speaker_audio_empty", state.speakerName) end
state.playing = false
end
 
local function search(ctx)
local query = lib.prompt(ctx, "Buscar música:", state.query)
if not query or not query:match("%S") then return end
state.query, state.results, state.page = query, {}, 1
state.searching, state.message = true, "Buscando..."
local url = API .. "?v=" .. VERSION .. "&search=" .. textutils.urlEncode(query)
state.searchUrl = url
local ok, reason = pcall(http.request, url)
if not ok then state.searching = false; state.message = "HTTP no disponible: " .. tostring(reason) end
end
 
local function playItem(ctx, item)
local speaker = peripherals.find("speaker")
if not speaker then state.message = "Conecta un speaker para reproducir."; return end
local ok, decoderModule = pcall(require, "cc.audio.dfpwm")
if not ok then state.message = "Requiere CC:Tweaked 1.100+ (cc.audio.dfpwm)."; return end
stopPlayback()
state.stop, state.playing, state.speaker, state.now = false, true, speaker, item
local nameOk, speakerName = pcall(peripheral.getName, speaker)
state.speakerName = nameOk and speakerName or nil
state.message = "Cargando: " .. compact(item.name, 24)
state.decoder = decoderModule.make_decoder()
state.downloadUrl = API .. "?v=" .. VERSION .. "&id=" .. textutils.urlEncode(item.id)
local ok, reason = pcall(http.request, { url = state.downloadUrl, binary = true })
if not ok then state.playing = false; state.message = "HTTP no disponible: " .. tostring(reason) end
end
 
function M.event(ctx, event, url, handle)
if event == "http_success" then
if url == state.searchUrl then
local raw = handle.readAll(); handle.close()
local ok, data = pcall(textutils.unserialiseJSON, raw or "[]")
if not ok or type(data) ~= "table" then
state.searching, state.message = false, "Respuesta de búsqueda no válida"; return
end
state.results = {}
for _, item in ipairs(data) do
if type(item) == "table" and item.id then
state.results[#state.results + 1] = { id = tostring(item.id),
name = tostring(item.name or "Sin título"), artist = tostring(item.artist or "YouTube") }
end
end
state.searching = false
state.message = #state.results > 0 and ("Resultados: " .. #state.results)
or "No se encontraron resultados."
elseif url == state.downloadUrl then
local input = handle
ctx.spawn(function()
local first = input.read(4)
if not first then input.close(); error("Audio vacío") end
local firstChunk = true
while not state.stop do
local chunk = input.read(16380)
if not chunk then break end
if firstChunk then chunk, firstChunk = first .. chunk, false end
local audio = state.decoder(chunk)
while not state.stop and not state.speaker.playAudio(audio) do
coroutine.yield("speaker_audio_empty", state.speakerName)
end
end
input.close()
if not state.stop then state.message = "Reproducción terminada" end
state.playing = false
end, function(err)
state.playing = false; state.message = "Error de audio: " .. tostring(err)
end)
end
elseif event == "http_failure" then
if url == state.searchUrl then
state.searching, state.message = false, "No se pudo conectar con el servicio de YouTube."
elseif url == state.downloadUrl then
state.playing, state.message = false, "No se pudo descargar el audio desde YouTube."
end
end
end
 
function M.open()
state.results, state.selected, state.page = {}, 1, 1
state.message = "Busca una canción o pega un enlace de YouTube."
end
 
function M.close() stopPlayback() end
 
function M.draw()
local w, h = term.getSize()
ui.fill(2, 3, math.max(1, w - 3), 1, colors.blue)
ui.text(3, 3, compact(state.now and ("♫ " .. state.now.name) or "Streaming Music", w - 4), colors.white, colors.blue, w - 4)
ui.button(2, 5, 8, "Buscar", colors.green, colors.black)
ui.button(12, 5, 7, "Parar", colors.red)
ui.text(2, 7, compact(state.message, w - 2), state.searching and colors.gray or colors.blue, colors.white, w - 2)
local first = (state.page - 1) * math.max(1, h - 10) + 1
for index = first, math.min(#state.results, first + h - 11) do
local y, item = 9 + index - first, state.results[index]
ui.text(2, y, (index == state.selected and "> " or " ") .. compact(item.name, w - 4),
index == state.selected and colors.blue or colors.black, colors.white, w - 2)
if y + 1 < h - 1 then ui.text(4, y + 1, compact(item.artist, w - 6), colors.gray, colors.white, w - 4) end
end
end
 
function M.touch(ctx, x, y)
local _, h = term.getSize()
if y == 5 and x < 11 then search(ctx); return end
if y == 5 and x >= 11 and x < 20 then stopPlayback(); state.message = "Reproducción detenida"; return end
if y >= 9 and y < h - 1 then
local index = (state.page - 1) * math.max(1, h - 10) + math.floor((y - 9) / 2) + 1
if state.results[index] then state.selected = index; playItem(ctx, state.results[index]) end
end
end
 
return M
]],
['apps/ie.lua'] = [[local ui = require("sys.mobile.ui")
local lib = require("sys.mobile.lib")
 
local M = { title = "Internet Explorer Mobile" }
local MAX_BYTES = 24000
local state = { url = "", lines = {}, scroll = 1, message = "Elige un marcador o escribe una dirección." }
 
local function cleanHtml(html)
html = html:gsub("<[Ss][Cc][Rr][Ii][Pp][Tt][^>]*>.-</[Ss][Cc][Rr][Ii][Pp][Tt]%s*>", " ")
html = html:gsub("<[Ss][Tt][Yy][Ll][Ee][^>]*>.-</[Ss][Tt][Yy][Ll][Ee]%s*>", " ")
html = html:gsub("<[Hh][Rr][^>]*>", "\n----------------\n")
html = html:gsub("<[Bb][Rr][^>]*>", "\n")
html = html:gsub("</[Pp]%s*>", "\n\n")
html = html:gsub("</[Dd][Ii][Vv]%s*>", "\n")
html = html:gsub("<[Ll][Ii][^>]*>", "\n * ")
html = html:gsub("<[^>]->", " ")
html = html:gsub("&nbsp;", " "):gsub("&amp;", "&"):gsub("&lt;", "<")
html = html:gsub("&gt;", ">"):gsub("&quot;", '"'):gsub("&#39;", "'")
html = html:gsub("&#(%d+);", function(n) return string.char(tonumber(n) % 128) end)
html = html:gsub("[%z\1-\8\11\12\14-\31\127]", " ")
html = html:gsub("[ \t\r]+", " ")
html = html:gsub(" *\n *", "\n")
html = html:gsub("\n\n\n+", "\n\n")
return html
end
 
local function showText(text, width)
local result = {}
for paragraph in (text .. "\n"):gmatch("(.-)\n") do
local wrapped = ui.wrap(paragraph, width)
for _, line in ipairs(wrapped) do
if line ~= "" or #result == 0 or result[#result] ~= "" then
result[#result + 1] = line
end
end
end
return result
end
 
local function browse(url)
if not http or not http.get then
state.message = "HTTP API desactivada en la configuración de CraftOS."
state.lines = { state.message }
return
end
if type(url) ~= "string" or not url:match("^https?://[%w%.%-]+") or url:find("%s") then
state.message = "Dirección no válida. Usa http:// o https://"
state.lines = { state.message }
return
end
state.url, state.scroll = url, 1
local ok, response, reason = pcall(http.get, url, { ["User-Agent"] = "Android-Mobile/1.0" })
if not ok or not response then
state.message = "No se pudo abrir la página: " .. tostring(ok and reason or response)
state.lines = { state.message }
return
end
local chunks, size = {}, 0
local readOk, readError = pcall(function()
while size < MAX_BYTES do
local piece = response.read(math.min(2048, MAX_BYTES - size))
if not piece or piece == "" then break end
chunks[#chunks + 1] = piece
size = size + #piece
end
end)
pcall(response.close)
if not readOk then
state.message = "Error leyendo la pagina: " .. tostring(readError)
state.lines = { state.message }
return
end
local body = cleanHtml(table.concat(chunks))
if body == "" then body = "(La página no contiene texto legible.)" end
state.lines = showText(body, math.max(12, term.getSize() - 4))
state.message = "Descargado (" .. size .. " bytes; vista de texto)"
end
 
local function openAddress(ctx)
local url = lib.prompt(ctx, "URL:", state.url)
if url and url ~= "" then browse(url) end
end
 
function M.open()
state.url, state.scroll = "", 1
state.lines, state.message = {}, "Elige un marcador o escribe una direccion."
end
 
function M.draw(ctx)
local w, h = term.getSize()
ui.button(2, 3, math.min(w - 2, 14), "Direccion", colors.blue)
ui.text(2, 4, state.url ~= "" and state.url or "Toca [Direccion]", colors.black, colors.white, w - 2)
local bookmarks = ctx.settings.bookmarks or {}
ui.text(2, 5, "Favoritos:", colors.blue, colors.white)
for i = 1, math.min(3, #bookmarks) do
ui.button(2 + (i - 1) * math.floor(math.max(1, w - 2) / 3), 6,
math.floor(math.max(1, w - 2) / 3), bookmarks[i][1], colors.lightBlue)
end
ui.fill(1, 7, w, 1, colors.gray)
ui.text(2, 7, state.message, colors.white, colors.gray, w - 2)
for row = 9, h - 2 do
local line = state.lines[state.scroll + row - 9]
if line then ui.text(2, row, line, colors.black, colors.white, w - 2) end
end
ui.button(2, h - 1, 8, "Arriba", colors.blue)
ui.button(11, h - 1, 8, "Abajo", colors.blue)
end
 
function M.touch(ctx, x, y)
local w, h = term.getSize()
if y == 3 or (y == 4 and x <= w) then
openAddress(ctx)
elseif y == 6 then
local bookmarks = ctx.settings.bookmarks or {}
local slot = math.floor((x - 2) / math.max(1, math.floor((w - 2) / 3))) + 1
if bookmarks[slot] then browse(bookmarks[slot][2]) end
elseif y == h - 1 then
if x < 11 then state.scroll = math.max(1, state.scroll - 5)
else state.scroll = math.min(math.max(1, #state.lines - (h - 10)), state.scroll + 5) end
else
state.scroll = math.max(1, math.min(#state.lines, state.scroll + (y > h / 2 and 2 or -2)))
end
end
 
function M.drag(ctx, _, _, y)
local _, h = term.getSize()
state.scroll = math.max(1, math.min(#state.lines, state.scroll + (y > h / 2 and 2 or -2)))
end
 
return M
]],
['apps/radar.lua'] = [[local ui = require("sys.mobile.ui")
local sensors = require("sys.mobile.sensors")
local M = { title = "Sensor Hub / Radar" }
local state = { players = {}, error = nil, page = "players" }
 
local function refresh(ctx)
local players, err = sensors.players(ctx.settings.playerRange)
state.players, state.error = players or {}, err
end
 
function M.open(ctx)
refresh(ctx)
end
 
function M.refresh(ctx)
refresh(ctx)
end
 
function M.draw(ctx)
local w, h = term.getSize()
ui.button(2, 3, 12, "Actualizar", colors.blue)
ui.button(16, 3, 10, "Ambiente", colors.lightBlue)
if state.page == "world" then
local world = ctx.world or {}
ui.text(2, 5, "Bioma: " .. tostring(world.biome or "--"), colors.black, colors.white, w - 2)
ui.text(2, 6, "Dimension: " .. tostring(world.dimension or "--"), colors.black, colors.white, w - 2)
ui.text(2, 7, "Luna: " .. tostring(world.moon or "--") .. " Hora: "
.. tostring(world.worldTime or "--"), colors.black, colors.white, w - 2)
ui.text(2, 8, "Clima: " .. tostring(world.weather or "--"), colors.black, colors.white, w - 2)
else
ui.text(2, 5, "Player Detector | rango " .. tostring(ctx.settings.playerRange) .. "m", colors.blue, colors.white, w - 2)
if state.error then
ui.text(2, 7, state.error, colors.red, colors.white, w - 2)
elseif #state.players == 0 then
ui.text(2, 7, "No hay jugadores en rango.", colors.gray, colors.white)
else
for i, player in ipairs(state.players) do
local y = 7 + (i - 1) * 3
if y < h - 1 then
ui.fill(2, y, w - 3, 2, colors.lightGray)
ui.text(3, y, player.name .. " (" .. tostring(player.distance) .. "m)", colors.blue, colors.lightGray, w - 4)
ui.text(3, y + 1, "X:" .. tostring(player.x) .. " Y:" .. tostring(player.y) .. " Z:" .. tostring(player.z),
colors.black, colors.lightGray, w - 4)
end
end
end
end
end
 
function M.touch(ctx, x, y)
if y == 3 and x < 15 then refresh(ctx)
elseif y == 3 and x >= 15 then state.page = state.page == "world" and "players" or "world" end
end
 
return M
]],
['apps/messenger.lua'] = [[local ui = require("sys.mobile.ui")
local lib = require("sys.mobile.lib")
local peripherals = require("sys.mobile.peripherals")
local M = { title = "MSN Messenger Mobile" }
local state = { log = {}, chatBox = nil, target = "all", lastSent = -2 }
 
local function add(message)
state.log[#state.log + 1] = tostring(message)
while #state.log > 20 do table.remove(state.log, 1) end
end
 
function M.open()
state.chatBox = peripherals.find("chatBox")
end
 
function M.draw()
local w, h = term.getSize()
ui.text(2, 3, state.chatBox and "Conectado al Chat Box" or "Chat Box no conectado",
state.chatBox and colors.green or colors.red, colors.white, w - 2)
ui.button(2, 5, 9, "Unidad", colors.blue)
ui.text(12, 5, "Destino: " .. tostring(state.target), colors.black, colors.white, w - 13)
ui.button(2, 6, 6, "Hold", colors.red)
ui.button(8, 6, 7, "Seguir", colors.green)
ui.button(15, 6, 6, "Volver", colors.orange)
ui.button(21, 6, 6, "Estado", colors.blue)
ui.button(2, 8, 18, "Mensaje libre", colors.gray)
local first = math.max(1, #state.log - math.max(0, h - 11))
for i = first, #state.log do
ui.text(2, 10 + i - first, state.log[i], colors.black, colors.white, w - 2)
end
end
 
local function sendMessage(ctx, message)
if not state.chatBox then
state.chatBox = peripherals.find("chatBox")
if not state.chatBox then ctx.notify("Conecta un Chat Box de Advanced Peripherals."); return end
end
local fn = state.chatBox.sendMessage
if type(fn) ~= "function" then
ctx.notify("Este Chat Box no ofrece sendMessage.")
else
if os.clock() - state.lastSent < 1 then
ctx.notify("Espera un segundo antes de enviar otro mensaje.")
return
end
local ok, sent, reason = pcall(fn, message, "Android Mobile", "[]")
if ok and sent then
state.lastSent = os.clock()
add("Yo: " .. message)
ctx.notify("Mensaje enviado")
if ctx.playSound then ctx.playSound("action") end
else ctx.notify("Error de Chat Box: " .. tostring(ok and reason or sent)) end
end
end

function M.touch(ctx, x, y)
if y == 5 and x >= 2 and x <= 10 then
local target = lib.prompt(ctx, "Nombre del androide o all:", state.target)
if target and target ~= "" then state.target = target end
return
end
if y == 6 then
local command
if x >= 2 and x <= 7 then command = "hold"
elseif x >= 8 and x <= 14 then command = "resume"
elseif x >= 15 and x <= 20 then command = "return"
elseif x >= 21 and x <= 26 then command = "status" end
if command then sendMessage(ctx, "!jarvis " .. state.target .. " " .. command) end
return
end
if y == 8 and x >= 2 and x <= 19 then
local message = lib.prompt(ctx, "Mensaje (vacio cancela):", "")
if message and message ~= "" then sendMessage(ctx, message) end
end
end
 
function M.event(ctx, event, user, message)
if event == "chat" or event == "chat_message" then
local line = tostring(user or "Servidor") .. ": " .. tostring(message or "")
add(line)
end
end
 
return M
]],
['apps/create.lua'] = [[local ui = require("sys.mobile.ui")
local sensors = require("sys.mobile.sensors")
local outputDriver = require("drivers.create")
local M = { title = "Create Controller Mobile" }
local state = { telemetry = nil, error = nil, outputs = { clutch = false, gearbox = false, sequencer = false } }
local keys = { "clutch", "gearbox", "sequencer" }
 
local function update(ctx)
local telemetry, err = sensors.createTelemetry(ctx.settings)
state.telemetry, state.error = telemetry, err
end
 
function M.refresh(ctx)
update(ctx)
end
 
function M.open(ctx)
state.outputs = outputDriver.readOutputs(ctx.settings)
update(ctx)
end
 
function M.draw(ctx)
local w, h = term.getSize()
local t = state.telemetry
ui.button(2, 3, 13, "Actualizar", colors.blue)
if t then
ui.text(2, 5, "RPM: " .. tostring(t.rpm), colors.black, colors.white, w - 2)
ui.text(2, 6, "Stress: " .. tostring(t.stress) .. " / " .. tostring(t.capacity or "?"),
colors.black, colors.white, w - 2)
local health = t.overstressed and "OVERSTRESS" or (t.stalled and "STALLED" or "NORMAL")
ui.text(2, 7, "Estado: " .. health, t.overstressed and colors.red or colors.green, colors.white, w - 2)
if type(t.stress) == "number" and type(t.capacity) == "number" and t.capacity > 0 then
local width = math.max(1, math.min(20, w - 4))
local filled = math.max(0, math.min(width, math.floor(width * t.stress / t.capacity)))
ui.text(2, 8, string.rep("#", filled) .. string.rep("-", width - filled),
t.overstressed and colors.red or colors.green, colors.white, width)
end
else
ui.text(2, 5, state.error or "Sin telemetria", colors.red, colors.white, w - 2)
end
ui.text(2, 9, "Salidas (redstone local o Integrator):", colors.blue, colors.white, w - 2)
for i, key in ipairs(keys) do
local y = 11 + (i - 1) * 2
if y < h then
ui.button(2, y, math.min(w - 3, 24), key .. ": " .. (state.outputs[key] and "ON" or "OFF"),
state.outputs[key] and colors.green or colors.gray, colors.white)
end
end
end
 
function M.touch(ctx, x, y)
if y == 3 then update(ctx); return end
if y >= 11 and y <= 15 then
local index = math.floor((y - 11) / 2) + 1
local key = keys[index]
if key then
local nextValue = not state.outputs[key]
local ok, err = pcall(outputDriver.setOutput, ctx.settings, key, nextValue)
if ok then
state.outputs[key] = nextValue
if ctx.playSound then ctx.playSound("action") end
else ctx.notify("Create: " .. tostring(err)) end
end
end
end
 
return M
]],
['apps/explorer.lua'] = [[local ui = require("sys.mobile.ui")
local lib = require("sys.mobile.lib")
local M = { title = "Mi PC - Explorador" }
local state = { path = "/", entries = {}, selected = nil, text = nil, lines = {}, error = nil }
 
local function refresh()
if not fs.exists(state.path) or not fs.isDir(state.path) then state.path = "/" end
state.entries = fs.list(state.path)
table.sort(state.entries, function(a, b)
local ad = fs.isDir(fs.combine(state.path, a))
local bd = fs.isDir(fs.combine(state.path, b))
if ad ~= bd then return ad end
return a:lower() < b:lower()
end)
state.selected, state.text, state.lines, state.error = nil, nil, {}, nil
end
 
local function openEntry(ctx, entry)
local path = fs.combine(state.path, entry)
if fs.isDir(path) then
state.path = path
refresh()
else
state.selected = path
local content, err = lib.readFile(path, 12000)
state.text, state.error = content or "", err
state.lines = {}
for line in ((state.text or "") .. "\n"):gmatch("(.-)\n") do
state.lines[#state.lines + 1] = line
end
end
end
 
function M.open()
state.path = "/"
refresh()
end
 
function M.draw(ctx)
local w, h = term.getSize()
ui.text(2, 3, state.path, colors.blue, colors.white, w - 2)
ui.button(2, 4, 8, "Arriba", colors.blue)
ui.button(12, 4, 9, "Actualizar", colors.lightBlue)
if state.selected then
local name = fs.getName(state.selected)
ui.text(2, 6, name .. (state.error and " (error)" or ""), colors.blue, colors.white, w - 2)
if state.error then
ui.text(2, 7, tostring(state.error), colors.red, colors.white, w - 2)
end
for i = 1, math.min(5, math.max(0, h - 10)) do
if state.lines[i] then ui.text(2, 6 + i + (state.error and 1 or 0), state.lines[i],
colors.black, colors.white, w - 2) end
end
ui.button(2, h - 2, 9, "Editar", colors.green, colors.black)
ui.button(13, h - 2, 9, "Cerrar", colors.gray)
else
for i, entry in ipairs(state.entries) do
local y = 6 + i
if y >= h - 2 then break end
local path = fs.combine(state.path, entry)
local prefix = fs.isDir(path) and "[+] " or " "
ui.text(2, y, prefix .. entry, fs.isDir(path) and colors.blue or colors.black, colors.white, w - 2)
end
end
end
 
function M.touch(ctx, x, y)
local _, h = term.getSize()
if y == 4 then
if x <= 10 then state.path = fs.getDir(state.path); if state.path == "" then state.path = "/" end; refresh()
else refresh() end
elseif state.selected then
if y == h - 2 and x < 12 then
local current, lines = lib.readFile(state.selected, 12000)
if not current then ctx.notify("No se pudo leer: " .. tostring(lines)); return end
ui.fill(1, 3, term.getSize(), h - 4, colors.black)
print("Editor Android - " .. fs.getName(state.selected))
print("Escribe el texto por lineas; una linea con . guarda.")
local replacement = {}
while true do
local line = read()
if line == "." then break end
replacement[#replacement + 1] = line
end
local ok, err = lib.writeFile(state.selected, table.concat(replacement, "\n") .. "\n")
if ok then ctx.notify("Archivo guardado") else ctx.notify("Error: " .. tostring(err)) end
refresh()
elseif y == h - 2 then
state.selected, state.text, state.lines = nil, nil, {}
end
elseif y >= 7 then
local index = y - 6
if state.entries[index] then openEntry(ctx, state.entries[index]) end
end
end
 
return M
]],
['apps/media.lua'] = [[local ui = require("sys.mobile.ui")
local peripherals = require("sys.mobile.peripherals")
local M = { title = "Android Media Player" }
local state = { tracks = {}, selected = 1, playing = false, stop = false, speaker = nil, message = "DFPWM en /music o /disk" }
 
local function scan()
state.tracks = {}
for _, directory in ipairs({ "/music", "/disk" }) do
if fs.exists(directory) and fs.isDir(directory) then
for _, name in ipairs(fs.list(directory)) do
if not fs.isDir(fs.combine(directory, name)) and name:lower():match("%.dfpwm$") then
state.tracks[#state.tracks + 1] = fs.combine(directory, name)
end
end
end
end
if state.selected > #state.tracks then state.selected = math.max(1, #state.tracks) end
end
 
function M.open()
scan()
end
 
function M.draw()
local w, h = term.getSize()
ui.fill(2, 3, w - 3, 2, colors.blue)
ui.center(3, "Android Media Player", colors.white, colors.blue)
ui.button(2, 6, 7, "Play", colors.green, colors.black)
ui.button(10, 6, 8, "Pause", colors.lightBlue)
ui.button(20, 6, 7, "Stop", colors.red)
ui.button(2, 8, 10, "Actualizar", colors.gray)
ui.text(2, 10, state.message, colors.blue, colors.white, w - 2)
for i, track in ipairs(state.tracks) do
local y = 12 + i - 1
if y >= h - 1 then break end
ui.text(2, y, (i == state.selected and "> " or " ") .. fs.getName(track),
i == state.selected and colors.blue or colors.black, colors.white, w - 2)
end
end
 
local function play(ctx)
local path = state.tracks[state.selected]
if not path then state.message = "No hay pistas .dfpwm"; return end
if state.playing then state.message = "Ya hay una pista activa"; return end
local speaker = peripherals.find("speaker")
if not speaker then state.message = "Speaker no conectado"; return end
local ok, decoderModule = pcall(require, "cc.audio.dfpwm")
if not ok then state.message = "No está disponible cc.audio.dfpwm"; return end
local input, err = fs.open(path, "rb")
if not input then state.message = tostring(err); return end
state.speaker, state.stop, state.playing = speaker, false, true
state.message = "Reproduciendo: " .. fs.getName(path)
local decoder = decoderModule.make_decoder()
local speakerName
if peripheral and peripheral.getName then
local nameOk, name = pcall(peripheral.getName, speaker)
if nameOk then speakerName = name end
end
local function playbackFailed(reason)
pcall(input.close)
state.playing = false
state.message = "Error de audio: " .. tostring(reason)
end
ctx.spawn(function()
while not state.stop do
local compressed = input.read(8192)
if not compressed then break end
local audio = decoder(compressed)
while not state.stop do
local queued, accepted = pcall(speaker.playAudio, audio)
if not queued then error(accepted) end
if accepted then break end
coroutine.yield("speaker_audio_empty", speakerName)
end
end
input.close()
state.playing = false
state.message = "Reproducción terminada"
end, playbackFailed)
end
 
function M.touch(ctx, x, y)
if y == 6 and x < 9 then play(ctx)
elseif y == 6 and x < 19 then
state.stop = true
if state.speaker and state.speaker.stop then pcall(state.speaker.stop) end
state.message = "Pausado; Play reinicia la pista"
elseif y == 6 then
state.stop = true
if state.speaker and state.speaker.stop then pcall(state.speaker.stop) end
state.message = "Deteniendo..."
elseif y == 8 then scan()
elseif y >= 12 then
local index = y - 11
if state.tracks[index] then state.selected = index end
end
end
 
return M
]],
['apps/cmd.lua'] = [[local ui = require("sys.mobile.ui")
local lib = require("sys.mobile.lib")
local M = { title = "Pocket CMD" }
local state = { history = { "Android Terminal. Toca Ejecutar para introducir un comando." } }
 
function M.draw()
local w, h = term.getSize()
local clearX = w - 8
if w >= 20 then
ui.button(2, 3, clearX - 3, "Ejecutar", colors.blue)
ui.button(clearX, 3, w - clearX, "Limpiar", colors.gray)
else
ui.button(2, 3, w - 2, "Ejecutar", colors.blue)
end
for i = math.max(1, #state.history - (h - 7)), #state.history do
ui.text(2, 5 + i - math.max(1, #state.history - (h - 7)), state.history[i], colors.black, colors.white, w - 2)
end
end
 
function M.touch(ctx, x, y)
if y ~= 3 then return end
local width = term.getSize()
if width >= 20 and x >= width - 8 then state.history = {}; return end
local command = lib.prompt(ctx, ">", "")
if not command or command == "" then return end
state.history[#state.history + 1] = "> " .. command
local previous = term.current()
local width, height = term.getSize()
local output = window.create(previous, 1, 1, width, height, true)
term.redirect(output)
local ok, result = pcall(function()
if shell and shell.execute then return shell.execute(command) end
return shell.run(command)
end)
term.redirect(previous)
for row = 1, height do
local line = output.getLine(row)
local text = type(line) == "table" and line[1] or line
if text and text:match("%S") then state.history[#state.history + 1] = text:gsub("%s+$", "") end
end
if not ok then
state.history[#state.history + 1] = "Error: " .. tostring(result)
elseif result == false then
state.history[#state.history + 1] = "Comando no encontrado o fallido."
else
state.history[#state.history + 1] = "Comando terminado."
end
end
 
return M
]],
['apps/settings.lua'] = [[local ui = require("sys.mobile.ui")
local lib = require("sys.mobile.lib")
local M = { title = "Ajustes" }
 
function M.draw(ctx)
local w = term.getSize()
ui.text(2, 3, "Configuracion del sistema", colors.blue, colors.white, w - 2)
ui.button(2, 5, 23, "Rango radar: " .. tostring(ctx.settings.playerRange) .. "m", colors.lightBlue)
ui.button(2, 7, 23, "Modo Create: " .. (ctx.settings.createOutputMode or "redstone"), colors.lightBlue)
ui.button(2, 9, 23, "Sonido: " .. (ctx.settings.clickSounds == false and "OFF" or "ON"), colors.lightBlue)
ui.text(2, 10, "Periferico Create: " .. (ctx.settings.createPeripheral or "auto"), colors.black, colors.white, w - 2)
ui.button(2, 11, 18, "Guardar ajustes", colors.green, colors.black)
ui.text(2, 13, "Salidas Create:", colors.blue, colors.white)
for i, key in ipairs({ "clutch", "gearbox", "sequencer" }) do
local side = ctx.settings.createOutputs[key]
ui.text(2, 14 + i, key .. ": " .. tostring(side), colors.black, colors.white, w - 2)
end
ui.text(2, 18, "Valores editables desde /sys/mobile/settings", colors.gray, colors.white, w - 2)
end
 
function M.touch(ctx, x, y)
if y == 5 then
local value = tonumber(lib.prompt(ctx, "Rango radar (1-128):", tostring(ctx.settings.playerRange)))
if value and value >= 1 and value <= 128 then ctx.settings.playerRange = math.floor(value) end
elseif y == 7 then
ctx.settings.createOutputMode = ctx.settings.createOutputMode == "integrator" and "redstone" or "integrator"
elseif y == 9 then
ctx.settings.clickSounds = ctx.settings.clickSounds == false
elseif y == 11 then
local ok, err = pcall(ctx.saveSettings)
ctx.notify(ok and "Configuracion guardada" or tostring(err))
if ok and ctx.playSound then ctx.playSound("action") end
elseif y >= 15 and y <= 17 then
local key = ({ "clutch", "gearbox", "sequencer" })[y - 14]
local side = lib.prompt(ctx, "Lado para " .. key .. ":", ctx.settings.createOutputs[key])
if side and side ~= "" then ctx.settings.createOutputs[key] = side:lower() end
end
end
 
return M
]],
['apps/gps.lua'] = [[local ui = require("sys.mobile.ui")
local M = { title = "Pocket GPS" }
local state = {
x = nil,
y = nil,
z = nil,
message = "Pulsa Localizar para buscar una senal GPS.",
lastFix = nil,
}
 
local function locate()
if not gps or type(gps.locate) ~= "function" then
state.message = "GPS API no disponible en este equipo."
return
end
state.message = "Buscando satelites..."
local ok, x, y, z = pcall(gps.locate, 3, false)
if not ok then
state.message = "Error GPS: " .. tostring(x)
return
end
if type(x) ~= "number" or type(y) ~= "number" or type(z) ~= "number" then
state.message = "Sin senal GPS. Requiere 4 hosts GPS."
return
end
state.x, state.y, state.z = x, y, z
state.lastFix = os.date("%H:%M:%S")
state.message = "Posicion obtenida a las " .. state.lastFix
end
 
function M.open()
state.message = state.x and ("Ultima posicion: " .. tostring(state.lastFix))
or "Pulsa Localizar para buscar una senal GPS."
end
 
function M.draw()
local w, h = term.getSize()
ui.fill(2, 3, math.max(1, w - 3), 2, colors.blue)
ui.text(3, 3, "POSICION DEL ORDENADOR", colors.white, colors.blue, w - 5)
if state.x then
ui.text(3, 4, string.format("X %.2f Y %.2f Z %.2f", state.x, state.y, state.z),
colors.white, colors.blue, w - 5)
else
ui.text(3, 4, "X -- Y -- Z --", colors.white, colors.blue, w - 5)
end
ui.button(2, 6, math.min(12, w - 2), "Localizar", colors.green, colors.black)
ui.text(2, 8, state.message, colors.black, colors.white, w - 2)
ui.fill(2, 10, math.max(1, w - 3), 1, colors.lightBlue)
ui.text(3, 10, "Como funciona", colors.white, colors.lightBlue, w - 5)
ui.text(2, 12, "GPS usa la API nativa de CC:Tweaked.", colors.black, colors.white, w - 2)
ui.text(2, 13, "Necesitas 4 ordenadores GPS host", colors.black, colors.white, w - 2)
ui.text(2, 14, "con modem y gps host en cada uno.", colors.black, colors.white, w - 2)
if h > 16 then ui.text(2, h - 2, "Las coords son del ordenador, no del jugador.", colors.gray, colors.white, w - 2) end
end
 
function M.touch(ctx, x, y)
if y == 6 and x <= 14 then
locate()
if state.x and ctx.playSound then ctx.playSound("action") end
end
end
 
return M
]],
['apps/ores.lua'] = [[local ui = require("sys.mobile.ui")
local M = { title = "Advanced Peripherals Ore Detector" }
local state = { radius = 8, ores = {}, message = "Conecta un Geo Scanner y pulsa Escanear." }
 
local function isOre(block)
if type(block) ~= "table" then return false end
local name = tostring(block.name or block.id or ""):lower()
if name:find("ore", 1, true) then return true end
if type(block.tags) == "table" then
for key, value in pairs(block.tags) do
local tag = type(key) == "string" and key or tostring(value)
if tag:lower():find("ores", 1, true) then return true end
end
end
return false
end
 
local function distanceSquared(block)
local x, y, z = tonumber(block.x), tonumber(block.y), tonumber(block.z)
if not x or not y or not z then return math.huge end
return x * x + y * y + z * z
end
 
local function scan()
if not peripheral or type(peripheral.find) ~= "function" then
state.message = "API de perifericos no disponible."
state.ores = {}
return
end
local okFind, scanner = pcall(peripheral.find, "geoScanner")
if not okFind or not scanner then
state.message = "Geo Scanner no conectado."
state.ores = {}
return
end
if type(scanner.scan) ~= "function" then
state.message = "El Geo Scanner no ofrece scan(radius)."
state.ores = {}
return
end
state.message = "Escaneando radio " .. state.radius .. "..."
local ok, result = pcall(scanner.scan, state.radius)
if not ok then
state.message = "Fallo al escanear: " .. tostring(result)
state.ores = {}
return
end
if type(result) ~= "table" then
state.message = "El Geo Scanner devolvio un resultado invalido."
state.ores = {}
return
end
state.ores = {}
for _, block in pairs(result) do
if isOre(block) then state.ores[#state.ores + 1] = block end
end
table.sort(state.ores, function(a, b) return distanceSquared(a) < distanceSquared(b) end)
state.message = #state.ores == 0 and "No se encontraron menas en el escaneo."
or (#state.ores .. " bloques con etiqueta/nombre de mena.")
end
 
function M.open()
state.ores = {}
state.message = "Conecta un Geo Scanner y pulsa Escanear."
end
 
function M.draw()
local w, h = term.getSize()
ui.button(2, 3, math.min(12, w - 2), "Escanear", colors.green, colors.black)
ui.button(16, 3, math.min(10, math.max(1, w - 16)), "Radio " .. state.radius, colors.lightBlue)
ui.text(2, 5, state.message, colors.blue, colors.white, w - 2)
ui.fill(2, 6, math.max(1, w - 3), 1, colors.blue)
ui.text(3, 6, "MENAS MAS CERCANAS", colors.white, colors.blue, w - 5)
if #state.ores == 0 then
ui.text(2, 8, "El resultado aparecera aqui.", colors.gray, colors.white, w - 2)
return
end
for i = 1, math.min(#state.ores, h - 9) do
local block = state.ores[i]
local name = tostring(block.name or block.id or "mena")
name = name:gsub("^.+:", "")
local x, y, z = tostring(block.x or "?"), tostring(block.y or "?"), tostring(block.z or "?")
local row = 8 + i
ui.fill(2, row, math.max(1, w - 3), 1, i % 2 == 0 and colors.lightGray or colors.white)
ui.text(3, row, name .. " " .. x .. "," .. y .. "," .. z,
colors.black, i % 2 == 0 and colors.lightGray or colors.white, w - 5)
end
end
 
function M.touch(ctx, x, y)
local w = term.getSize()
if y == 3 and x <= 14 then
scan()
if state.message:find("bloques con etiqueta", 1, true) and ctx.playSound then
ctx.playSound("action")
end
elseif y == 3 and x >= 15 and x <= w then
state.radius = state.radius >= 8 and 1 or state.radius + 1
end
end
 
return M
]],
['apps/calculator.lua'] = [[local ui = require("sys.mobile.ui")
local lib = require("sys.mobile.lib")
local M = { title = "Pocket Calculator" }
local state = { expression = "", result = "Introduce una expresion." }
 
local function evaluate(source)
local position = 1
local parseExpression
local function skipSpace()
while source:sub(position, position):match("%s") do position = position + 1 end
end
local function parseNumber()
skipSpace()
local token = source:sub(position):match("^%d+%.?%d*") or source:sub(position):match("^%.%d+")
if not token then error("Se esperaba un numero") end
position = position + #token
return tonumber(token)
end
local function parsePrimary()
skipSpace()
if source:sub(position, position) == "(" then
position = position + 1
local value = parseExpression()
skipSpace()
if source:sub(position, position) ~= ")" then error("Falta cerrar parentesis") end
position = position + 1
return value
end
return parseNumber()
end
local parseUnary
local function parsePower()
local value = parsePrimary()
skipSpace()
if source:sub(position, position) == "^" then
position = position + 1
value = value ^ parseUnary()
end
return value
end
parseUnary = function()
skipSpace()
local char = source:sub(position, position)
if char == "+" then position = position + 1; return parseUnary() end
if char == "-" then position = position + 1; return -parseUnary() end
return parsePower()
end
local function parseTerm()
local value = parseUnary()
while true do
skipSpace()
local operator = source:sub(position, position)
if operator ~= "*" and operator ~= "/" and operator ~= "%" then break end
position = position + 1
local right = parseUnary()
if (operator == "/" or operator == "%") and right == 0 then error("Division entre cero") end
if operator == "*" then value = value * right
elseif operator == "/" then value = value / right
else value = value % right end
end
return value
end
parseExpression = function()
local value = parseTerm()
while true do
skipSpace()
local operator = source:sub(position, position)
if operator ~= "+" and operator ~= "-" then break end
position = position + 1
local right = parseTerm()
value = operator == "+" and value + right or value - right
end
return value
end
local result = parseExpression()
skipSpace()
if position <= #source then error("Caracter inesperado: " .. source:sub(position, position)) end
if result ~= result or result == math.huge or result == -math.huge then error("Resultado fuera de rango") end
return result
end
 
function M.draw()
local w = term.getSize()
ui.fill(2, 3, math.max(1, w - 3), 3, colors.black)
ui.text(3, 3, state.expression ~= "" and state.expression or "Pocket Calc",
colors.lightBlue, colors.black, w - 5)
ui.text(3, 4, tostring(state.result), colors.white, colors.black, w - 5)
ui.button(2, 7, math.min(16, w - 2), "Calcular", colors.green, colors.black)
ui.button(2, 9, math.min(16, w - 2), "Nueva cuenta", colors.lightBlue)
ui.text(2, 12, "Operadores: + - * / % ^", colors.gray, colors.white, w - 2)
ui.text(2, 13, "Admite parentesis y numeros decimales.", colors.gray, colors.white, w - 2)
end
 
function M.touch(ctx, _, y)
if y ~= 7 and y ~= 9 then return end
local expression = lib.prompt(ctx, "Cuenta:", state.expression)
if not expression or expression == "" then return end
state.expression = expression
local ok, result = pcall(evaluate, expression)
state.result = ok and tostring(result) or tostring(result)
if ok and ctx.playSound then ctx.playSound("action") end
end
 
return M
]],
['apps/notes.lua'] = [[local ui = require("sys.mobile.ui")
local lib = require("sys.mobile.lib")
local M = { title = "Pocket Notes" }
local PATH = "/notes/Quick Notes.txt"
local state = { text = "", message = "Notas guardadas en /notes." }
 
local function load()
if not fs.exists(PATH) then state.text = ""; return true end
local text, err = lib.readFile(PATH, 12000)
if not text then state.message = tostring(err); return false end
state.text, state.message = text, "Nota cargada."
return true
end
 
function M.open()
load()
end
 
function M.draw()
local w, h = term.getSize()
ui.button(2, 3, math.min(12, w - 2), "Editar", colors.green, colors.black)
ui.button(16, 3, math.min(10, math.max(1, w - 16)), "Guardar", colors.blue)
ui.text(2, 5, state.message, colors.blue, colors.white, w - 2)
ui.fill(2, 6, math.max(1, w - 3), 1, colors.lightBlue)
ui.text(3, 6, "QUICK NOTES", colors.white, colors.lightBlue, w - 5)
local lines = {}
for line in (state.text .. "\n"):gmatch("(.-)\n") do lines[#lines + 1] = line end
for i = 1, math.min(#lines, h - 9) do
ui.text(2, 7 + i, lines[i], colors.black, colors.white, w - 2)
end
end
 
function M.touch(ctx, x, y)
local w = term.getSize()
if y == 3 and x <= 14 then
print("Editor de notas. Escribe una linea con . para terminar.")
local lines = {}
while true do
local line = read()
if line == "." then break end
lines[#lines + 1] = line
end
state.text = table.concat(lines, "\n")
state.message = "Cambios sin guardar."
elseif y == 3 and x > 14 and x <= w then
local ok, err = lib.writeFile(PATH, state.text)
state.message = ok and "Nota guardada." or ("Error: " .. tostring(err))
if ok and ctx.playSound then ctx.playSound("action") end
end
end
 
return M
]],
['apps/clock.lua'] = [[local ui = require("sys.mobile.ui")
local lib = require("sys.mobile.lib")
local M = { title = "Pocket Clock" }
local state = { started = nil, elapsed = 0, timerEnd = nil, message = "Reloj y cronometro" }
 
local function now()
if os.epoch then return os.epoch("utc") / 1000 end
return os.clock()
end
 
local function elapsed()
return state.elapsed + (state.started and (now() - state.started) or 0)
end
 
local function format(seconds)
seconds = math.max(0, math.floor(seconds))
return string.format("%02d:%02d:%02d", math.floor(seconds / 3600), math.floor(seconds / 60) % 60, seconds % 60)
end
 
function M.draw(ctx)
local w = term.getSize()
local clock = os.date("%H:%M:%S")
ui.fill(2, 3, math.max(1, w - 3), 3, colors.blue)
ui.text(3, 3, "HORA LOCAL", colors.white, colors.blue, w - 5)
ui.text(3, 4, clock, colors.white, colors.blue, w - 5)
ui.text(2, 7, "Cronometro: " .. format(elapsed()), colors.black, colors.white, w - 2)
ui.button(2, 9, math.min(10, w - 2), state.started and "Pausar" or "Iniciar", colors.green, colors.black)
ui.button(14, 9, math.min(10, math.max(1, w - 14)), "Reiniciar", colors.lightBlue)
if state.timerEnd then
local remaining = state.timerEnd - now()
ui.text(2, 12, "Temporizador: " .. format(remaining), colors.blue, colors.white, w - 2)
else
ui.text(2, 12, state.message, colors.gray, colors.white, w - 2)
end
ui.button(2, 14, math.min(w - 2, 20), "Programar temporizador", colors.blue)
end
 
function M.tick(ctx)
if state.timerEnd and now() >= state.timerEnd then
state.timerEnd = nil
state.message = "Temporizador terminado."
ctx.notify("Temporizador terminado")
if ctx.playSound then ctx.playSound("action") end
end
end
 
function M.touch(ctx, x, y)
if y == 9 and x < 13 then
if state.started then
state.elapsed = elapsed()
state.started = nil
else
state.started = now()
end
elseif y == 9 then
state.started, state.elapsed, state.timerEnd = nil, 0, nil
elseif y == 14 then
local seconds = tonumber(lib.prompt(ctx, "Segundos (1-86400):", "60"))
if seconds and seconds >= 1 and seconds <= 86400 then
state.timerEnd = now() + math.floor(seconds)
state.message = "Temporizador activo."
else
state.message = "Duracion no valida."
end
end
end
 
return M
]],
['apps/redstone.lua'] = [[local ui = require("sys.mobile.ui")
local M = { title = "Pocket Redstone" }
local sides = { "left", "right", "top", "bottom", "front", "back" }
 
function M.draw()
local w = term.getSize()
ui.text(2, 3, "Salidas del ordenador", colors.blue, colors.white, w - 2)
if not redstone or not redstone.getOutput then
ui.text(2, 5, "API de redstone no disponible.", colors.red, colors.white, w - 2)
return
end
for i, side in ipairs(sides) do
local y = 5 + i * 2
local active = redstone.getOutput(side)
ui.button(2, y, math.min(w - 3, 20), side .. ": " .. (active and "ON" or "OFF"),
active and colors.green or colors.gray, colors.white)
end
ui.text(2, 18, "Toca una salida para conmutarla.", colors.gray, colors.white, w - 2)
end
 
function M.touch(ctx, _, y)
if not redstone or not redstone.getOutput or not redstone.setOutput then
ctx.notify("La API de redstone no esta disponible.")
return
end
local index = math.floor((y - 5) / 2)
local side = sides[index]
if side and y == 5 + index * 2 then
local ok, err = pcall(redstone.setOutput, side, not redstone.getOutput(side))
if not ok then ctx.notify("Redstone: " .. tostring(err))
elseif ctx.playSound then ctx.playSound("action") end
end
end
 
return M
]],
['apps/inventory.lua'] = [[local ui = require("sys.mobile.ui")
local M = { title = "Pocket Inventory" }
local state = { items = {}, message = "Pulsa Actualizar para leer inventarios.", scroll = 0 }
 
local function refresh()
state.items = {}
if not peripheral or type(peripheral.find) ~= "function" then
state.message = "API de perifericos no disponible."
return
end
local ok, inventories = pcall(peripheral.find, "inventory")
if not ok or not inventories then
state.message = "No se encontro un inventario conectado."
return
end
if type(inventories.list) ~= "function" then
state.message = "El periferico no ofrece list()."
return
end
local listed, contents = pcall(inventories.list)
if not listed or type(contents) ~= "table" then
state.message = "No se pudo leer el inventario."
return
end
for slot, item in pairs(contents) do
if type(item) == "table" then
local detail = item
if type(inventories.getItemDetail) == "function" then
local okDetail, value = pcall(inventories.getItemDetail, slot)
if okDetail and type(value) == "table" then detail = value end
end
state.items[#state.items + 1] = {
slot = slot,
name = detail.displayName or detail.name or item.name or "Objeto",
count = item.count or detail.count or 0,
}
end
end
table.sort(state.items, function(a, b) return a.slot < b.slot end)
state.scroll = 0
state.message = #state.items .. " stacks en el primer inventario detectado."
end
 
function M.open()
state.scroll = 0
end
 
function M.draw()
local w, h = term.getSize()
ui.button(2, 3, math.min(13, w - 2), "Actualizar", colors.green, colors.black)
ui.text(2, 5, state.message, colors.blue, colors.white, w - 2)
ui.fill(2, 6, math.max(1, w - 3), 1, colors.blue)
ui.text(3, 6, "SLOT OBJETO CANT.", colors.white, colors.blue, w - 5)
for i = 1, math.max(0, h - 12) do
local item = state.items[state.scroll + i]
if not item then break end
local name = item.name
local row = 6 + i
local bg = i % 2 == 0 and colors.lightGray or colors.white
ui.fill(2, row, math.max(1, w - 3), 1, bg)
ui.text(3, row, string.format("%-4s %-18s %5s", tostring(item.slot), name, tostring(item.count)),
colors.black, bg, w - 5)
end
ui.button(2, h - 2, math.min(8, w - 2), "Arriba", colors.lightBlue)
ui.button(math.max(11, w - 9), h - 2, math.min(8, w - 2), "Abajo", colors.lightBlue)
end
 
function M.touch(_, x, y)
local _, h = term.getSize()
if y == 3 then refresh()
elseif y == h - 2 and x < 10 then
state.scroll = math.max(0, state.scroll - 5)
elseif y == h - 2 then
state.scroll = math.min(math.max(0, #state.items - 1), state.scroll + 5)
end
end
 
return M
]],
['apps/youtube.lua'] = [[local ui = require("sys.mobile.ui")
local lib = require("sys.mobile.lib")
local M = { title = "YouTube Terminal" }
 
local DATA_PATH = "/sys/mobile/youtube"
local MAX_RESPONSE = 65536
local MAX_ITEMS = 40
local state = {
mode = "results",
query = "",
results = {},
saved = {},
history = {},
selected = nil,
returnMode = "results",
page = 1,
playbackLog = {},
message = "Busca videos. Requiere HTTP y un servidor Invidious disponible.",
}
 
local function loadData()
if not fs.exists(DATA_PATH) then return end
local file, err = fs.open(DATA_PATH, "r")
if not file then state.message = "No se pudo leer favoritos: " .. tostring(err); return end
local raw = file.readAll()
file.close()
local ok, data = pcall(textutils.unserialize, raw or "")
if not ok or type(data) ~= "table" then
state.message = "No se pudieron cargar favoritos e historial."
return
end
if type(data.saved) == "table" then state.saved = data.saved end
if type(data.history) == "table" then state.history = data.history end
end
 
local function saveData()
local parent = fs.getDir(DATA_PATH)
if not fs.exists(parent) then fs.makeDir(parent) end
local file, err = fs.open(DATA_PATH, "w")
if not file then return nil, err end
file.write(textutils.serialize({ saved = state.saved, history = state.history }))
file.close()
return true
end
 
local function readResponse(url, limit)
if not http or type(http.get) ~= "function" then
return nil, "HTTP no está habilitado en CraftOS."
end
local ok, response, reason = pcall(http.get, url, {
["User-Agent"] = "Android-Mobile/1.0",
["Accept"] = "application/json",
})
if not ok or not response then
return nil, "No se pudo conectar: " .. tostring(ok and reason or response)
end
 
local codeOk, code = pcall(function()
if response.getResponseCode then return response.getResponseCode() end
end)
if codeOk and type(code) == "number" and code >= 400 then
pcall(response.close)
return nil, "El servidor respondió HTTP " .. code .. "."
end
 
local chunks, size = {}, 0
local readOk, readError = pcall(function()
while size <= limit do
local chunk = response.read(math.min(4096, limit + 1 - size))
if not chunk or chunk == "" then break end
chunks[#chunks + 1] = chunk
size = size + #chunk
end
end)
pcall(response.close)
if not readOk then return nil, "Error leyendo la respuesta: " .. tostring(readError) end
if size > limit then return nil, "La respuesta supera el límite de tamaño." end
return table.concat(chunks)
end
 
local function decodeJson(raw)
local ok, result = pcall(textutils.unserializeJSON, raw or "")
if not ok or type(result) ~= "table" then
return nil, "El servidor devolvió JSON no válido."
end
return result
end
 
local function encode(value)
return (tostring(value):gsub("([^%w%-_%.~])", function(char)
return string.format("%%%02X", string.byte(char))
end))
end
 
local function findInstances()
local raw, err = readResponse("https://api.invidious.io/instances.json?sort_by=api", 32768)
if not raw then return nil, err end
local instances, decodeError = decodeJson(raw)
if not instances then return nil, decodeError end
 
local available = {}
for _, entry in ipairs(instances) do
local details = type(entry) == "table" and entry[2]
local uri = type(details) == "table" and details.uri
if type(uri) == "string" and uri:match("^https://")
and details.api == true and details.type == "https" then
available[#available + 1] = uri:gsub("/+$", "")
if #available >= 3 then break end
end
end
if #available == 0 then
return nil, "No se encontró un servidor HTTPS con API disponible."
end
return available
end
 
local function normalizeVideo(video)
if type(video) ~= "table" or type(video.videoId) ~= "string"
or not video.videoId:match("^[%w_-]+$")
or type(video.title) ~= "string" then
return nil
end
local title = video.title:gsub("[%c]", " "):gsub("%s+", " ")
local author = tostring(video.author or "Canal desconocido"):gsub("[%c]", " "):gsub("%s+", " ")
return {
videoId = video.videoId,
title = title,
author = author,
duration = tostring(video.lengthSeconds or "--"),
views = tostring(video.viewCount or "--"),
published = tostring(video.publishedText or ""),
}
end
 
local function search(query, page)
state.query = query
state.mode, state.selected = "results", nil
state.page = page or 1
state.results = {}
state.message = "Buscando..."
 
local instances, instanceError = findInstances()
if not instances then state.message = instanceError; return end
local lastError
for _, instance in ipairs(instances) do
local url = instance .. "/api/v1/search?q=" .. encode(query) .. "&type=video&page=" .. state.page
local raw, requestError = readResponse(url, MAX_RESPONSE)
if raw then
local response, decodeError = decodeJson(raw)
if response and type(response.error) ~= "string" then
for _, item in ipairs(response) do
local video = normalizeVideo(item)
if video then state.results[#state.results + 1] = video end
end
state.message = #state.results > 0
and ("Resultados: " .. #state.results .. " | Fuente: Invidious")
or "No se encontraron videos."
return
end
lastError = response and response.error or decodeError
else
lastError = requestError
end
end
state.message = lastError or "No se pudo completar la búsqueda."
end
 
local function activeItems()
if state.mode == "saved" then return state.saved end
if state.mode == "history" then return state.history end
return state.results
end
 
local function remember(video)
for index = #state.history, 1, -1 do
if state.history[index].videoId == video.videoId then
table.remove(state.history, index)
end
end
table.insert(state.history, 1, video)
while #state.history > MAX_ITEMS do table.remove(state.history) end
local ok, err = saveData()
if not ok then state.message = "No se guardó el historial: " .. tostring(err) end
end
 
local function openSearch(ctx)
local query = lib.prompt(ctx, "Buscar en YouTube:", state.query)
if query and query:match("%S") then search(query) end
end
 
local function selectVideo(video)
state.returnMode = state.mode
state.selected = video
state.mode = "detail"
remember(video)
end

local function playWithYouCube(ctx, video)
if not shell or type(shell.run) ~= "function" then
state.message = "No está disponible la API shell de CraftOS."
return
end
local url = "https://www.youtube.com/watch?v=" .. video.videoId
state.playbackLog = {}
state.message = "Abriendo YouCube..."
local previous = term.current()
local width, height = term.getSize()
local output
if window and type(window.create) == "function" then
local created, value = pcall(window.create, previous, 1, 1, width, height, true)
if created then output = value; term.redirect(output) end
end
local ok, result = pcall(shell.run, "/youcube/youcube.lua", "--verbose", url)
if output then
term.redirect(previous)
for row = 1, height do
local lineOk, line = pcall(output.getLine, row)
local text = lineOk and line
if type(text) == "string" and text:match("%S") then
state.playbackLog[#state.playbackLog + 1] = text:gsub("%s+$", "")
end
end
end
if not ok then
state.message = "No se pudo iniciar YouCube: " .. tostring(result)
elseif result == false then
state.message = "No se pudo iniciar el cliente YouCube incluido."
else
state.message = "YouCube terminó; revisa el diagnóstico de abajo."
end
end
 
function M.open()
loadData()
end
 
function M.draw()
local width, height = term.getSize()
ui.button(2, 3, 7, "Buscar", colors.red)
ui.button(10, 3, 8, "Favoritos", colors.lightBlue)
ui.button(19, 3, 8, "Historial", colors.lightBlue)
ui.fill(1, 4, width, 1, colors.gray)
ui.text(2, 4, state.message, colors.white, colors.gray, width - 2)
 
if state.mode == "detail" and state.selected then
local video = state.selected
ui.button(2, 5, 7, "Lista", colors.blue)
ui.button(10, 5, 8, "Guardar", colors.green, colors.black)
ui.button(20, 5, 9, "YouCube", colors.red)
ui.text(2, 7, video.title, colors.blue, colors.white, width - 2)
ui.text(2, 9, "Canal: " .. video.author, colors.black, colors.white, width - 2)
ui.text(2, 10, "Duracion: " .. video.duration .. " s", colors.black, colors.white, width - 2)
ui.text(2, 11, "Vistas: " .. video.views, colors.black, colors.white, width - 2)
ui.text(2, 13, "youtu.be/" .. video.videoId, colors.blue, colors.white, width - 2)
ui.text(2, 15, "YouCube conecta a servidores públicos.", colors.red, colors.white, width - 2)
if #state.playbackLog > 0 then
local first = math.max(1, #state.playbackLog - math.min(3, height - 17) + 1)
for row = 17, math.min(height - 2, 19) do
local line = state.playbackLog[first + row - 17]
if line then ui.text(2, row, line, colors.gray, colors.white, width - 2) end
end
else
ui.text(2, 16, "No se pudo conectar? toca YouCube para ver el error.", colors.gray, colors.white, width - 2)
end
return
end
 
local items = activeItems()
local firstRow, lastRow = 6, height - 3
local visible = math.max(0, lastRow - firstRow + 1)
local pageSize = math.max(1, math.floor(visible / 2))
local offset = state.mode == "results" and 0 or (state.page - 1) * pageSize
for itemIndex = 1, pageSize do
local item = items[offset + itemIndex]
if item then
local y = firstRow + (itemIndex - 1) * 2
local marker = state.mode == "results" and tostring(offset + itemIndex) or ">"
ui.text(2, y, marker .. " " .. item.title, colors.blue, colors.white, width - 2)
ui.fill(2, y + 1, math.max(1, width - 3), 1, colors.lightGray)
ui.text(3, y + 1, item.author .. " | " .. item.duration .. " s",
colors.gray, colors.lightGray, width - 4)
end
end
if visible > 0 then
ui.button(2, height - 1, 8, "< Atras", colors.lightBlue)
ui.button(math.max(11, width - 9), height - 1, 8, "Mas >", colors.lightBlue)
end
end
 
function M.touch(ctx, x, y)
local width, height = term.getSize()
if y == 3 then
if x < 10 then openSearch(ctx)
elseif x < 19 then
state.mode, state.page, state.selected = "saved", 1, nil
state.message = #state.saved .. " videos guardados."
else
state.mode, state.page, state.selected = "history", 1, nil
state.message = #state.history .. " videos en el historial."
end
return
end
 
if state.mode == "detail" and state.selected then
if y == 5 and x < 10 then
state.mode, state.selected = state.returnMode, nil
elseif y == 5 and x < 19 then
local exists = false
for _, item in ipairs(state.saved) do
if item.videoId == state.selected.videoId then exists = true; break end
end
if not exists then
table.insert(state.saved, 1, state.selected)
while #state.saved > MAX_ITEMS do table.remove(state.saved) end
end
local ok, err = saveData()
state.message = ok and "Video guardado en favoritos."
or ("No se guardó: " .. tostring(err))
elseif y == 5 and x >= 20 then
playWithYouCube(ctx, state.selected)
end
return
end
 
if y == height - 1 then
if x < 11 then
if state.mode == "results" and state.page > 1 then
search(state.query, state.page - 1)
else
state.page = math.max(1, state.page - 1)
end
elseif state.mode == "results" and state.query ~= "" then
search(state.query, state.page + 1)
else
local visible = math.max(1, height - 8)
local pageSize = math.max(1, math.floor(visible / 2))
if state.page * pageSize < #activeItems() then state.page = state.page + 1 end
end
return
end
 
local firstRow, lastRow = 6, height - 3
if y >= firstRow and y <= lastRow then
local visible = math.max(1, lastRow - firstRow + 1)
local pageSize = math.max(1, math.floor(visible / 2))
local offset = state.mode == "results" and 0 or (state.page - 1) * pageSize
local index = offset + math.floor((y - firstRow) / 2) + 1
local item = activeItems()[index]
if item then selectVideo(item) end
end
end
 
return M
]],
['apps/map.lua'] = [[local ui = require("sys.mobile.ui")
local lib = require("sys.mobile.lib")
local M = { title = "Google Maps Pocket" }
 
local DATA_PATH = "/sys/mobile/map"
local MAX_POINTS = 400
local SCALES = { 1, 4, 16, 64 }
local state = {
x = nil,
z = nil,
centerX = 0,
centerZ = 0,
dimension = "--",
points = {},
destination = nil,
zoom = 2,
dragX = nil,
dragY = nil,
message = "Sin GPS: centra el mapa e introduce X Z manualmente.",
}
 
local function save()
local parent = fs.getDir(DATA_PATH)
if not fs.exists(parent) then fs.makeDir(parent) end
local file, err = fs.open(DATA_PATH, "w")
if not file then return nil, err end
file.write(textutils.serialize({
points = state.points,
destination = state.destination,
zoom = state.zoom,
dimension = state.dimension,
x = state.x,
z = state.z,
centerX = state.centerX,
centerZ = state.centerZ,
}))
file.close()
return true
end
 
local function load()
if not fs.exists(DATA_PATH) then return end
local file, err = fs.open(DATA_PATH, "r")
if not file then state.message = "No se pudo leer el mapa: " .. tostring(err); return end
local raw = file.readAll()
file.close()
local ok, data = pcall(textutils.unserialize, raw or "")
if not ok or type(data) ~= "table" then
state.message = "No se pudieron cargar los datos del mapa."
return
end
if type(data.points) == "table" then
for _, point in ipairs(data.points) do
if type(point) == "table" and type(point.x) == "number" and type(point.z) == "number" then
state.points[#state.points + 1] = { x = point.x, z = point.z }
if #state.points >= MAX_POINTS then break end
end
end
end
if type(data.destination) == "table"
and type(data.destination.x) == "number"
and type(data.destination.z) == "number" then
state.destination = { x = data.destination.x, z = data.destination.z }
end
if type(data.zoom) == "number" and SCALES[data.zoom] then state.zoom = data.zoom end
if type(data.dimension) == "string" then state.dimension = data.dimension end
if type(data.centerX) == "number" and type(data.centerZ) == "number" then
state.centerX, state.centerZ = data.centerX, data.centerZ
end
if type(data.x) == "number" then state.x = data.x end
if type(data.z) == "number" then state.z = data.z end
if data.centerX == nil and #state.points > 0 then
local last = state.points[#state.points]
state.centerX, state.centerZ = last.x, last.z
state.x, state.z = last.x, last.z
end
end
 
local function recordPosition(x, z)
local previous = state.points[#state.points]
if not previous or math.floor(previous.x) ~= math.floor(x)
or math.floor(previous.z) ~= math.floor(z) then
state.points[#state.points + 1] = { x = x, z = z }
while #state.points > MAX_POINTS do table.remove(state.points, 1) end
return true
end
return false
end
 
local function parseCoordinates(value)
local x, z = tostring(value or ""):match("^%s*(-?[%d%.]+)%s*[, ]%s*(-?[%d%.]+)%s*$")
x, z = tonumber(x), tonumber(z)
if not x or not z or x ~= x or z ~= z
or x == math.huge or x == -math.huge or z == math.huge or z == -math.huge then
return nil
end
return x, z
end
 
local function setCenter(ctx)
local initial = state.x and (tostring(state.x) .. " " .. tostring(state.z))
or (tostring(state.centerX) .. " " .. tostring(state.centerZ))
local value = lib.prompt(ctx, "Posicion X Z (ej. 120 -45):", initial)
if value == nil or value == "" then return end
local x, z = parseCoordinates(value)
if not x then
state.message = "Coordenadas no validas. Escribe X Z."
return
end
state.x, state.z = x, z
state.centerX, state.centerZ = x, z
recordPosition(x, z)
state.message = "Mapa centrado en X " .. math.floor(x) .. " Z " .. math.floor(z) .. "."
local saved, err = save()
if not saved then state.message = "No se guardo el mapa: " .. tostring(err) end
end
 
local function setDestination(ctx)
local initial = state.destination
and (tostring(state.destination.x) .. " " .. tostring(state.destination.z)) or ""
local value = lib.prompt(ctx, "Destino X Z (vacio borra):", initial)
if value == nil then return end
if value == "" then
state.destination = nil
state.message = "Destino borrado."
else
local x, z = parseCoordinates(value)
if not x then
state.message = "Coordenadas no validas. Escribe X Z."
return
end
state.destination = { x = x, z = z }
state.message = "Destino guardado."
end
local saved, err = save()
if not saved then state.message = "No se guardó el mapa: " .. tostring(err) end
end
 
local function direction(dx, dz)
if dx == 0 and dz == 0 then return "aqui" end
local ax, az = math.abs(dx), math.abs(dz)
if az > ax * 2 then return dz < 0 and "N" or "S" end
if ax > az * 2 then return dx > 0 and "E" or "O" end
if dx > 0 then return dz < 0 and "NE" or "SE" end
return dz < 0 and "NO" or "SO"
end
 
local function controls(width)
if width < 22 then
return { centerX = 2, centerWidth = 5, minusX = 8, plusX = 11, zoomWidth = 2,
destinationX = 14, destinationWidth = math.max(1, width - 14) }
end
return { centerX = 2, centerWidth = 7, minusX = 10, plusX = 14, zoomWidth = 3,
destinationX = 18, destinationWidth = math.min(8, width - 17) }
end
 
local function drawMap(width, top, bottom)
local mapWidth = math.max(1, width - 4)
local mapHeight = math.max(1, bottom - top + 1)
local centerColumn = math.floor((mapWidth + 1) / 2)
local centerRow = math.floor((mapHeight + 1) / 2)
local scale = SCALES[state.zoom]
 
for row = 1, mapHeight do
local line = {}
for column = 1, mapWidth do
line[column] = (row == centerRow or column == centerColumn) and "+" or " "
end
 
local function plot(x, z, marker)
local column = centerColumn + math.floor((x - state.centerX) / scale + 0.5)
local row = centerRow + math.floor((z - state.centerZ) / scale + 0.5)
if column >= 1 and column <= mapWidth and row >= 1 and row <= mapHeight then
line[column] = marker
end
end
 
for _, point in ipairs(state.points) do plot(point.x, point.z, "o") end
if state.destination then plot(state.destination.x, state.destination.z, "X") end
if state.x and state.z then plot(state.x, state.z, "@") end
 
term.setCursorPos(3, top + row - 1)
for column = 1, mapWidth do
local marker = line[column]
if marker == "@" then
term.setTextColor(colors.white)
term.setBackgroundColor(colors.blue)
elseif marker == "X" then
term.setTextColor(colors.white)
term.setBackgroundColor(colors.red)
elseif marker == "+" then
term.setTextColor(colors.gray)
term.setBackgroundColor(colors.lightGray)
elseif marker == "o" then
term.setTextColor(colors.green)
term.setBackgroundColor(colors.white)
else
term.setTextColor(colors.lightGray)
term.setBackgroundColor(colors.white)
end
term.write(marker)
end
end
term.setBackgroundColor(colors.white)
end
 
function M.open(ctx)
state.x, state.z = nil, nil
state.centerX, state.centerZ = 0, 0
state.points = {}
state.destination = nil
state.zoom = 2
state.dimension = "--"
state.dragX, state.dragY = nil, nil
load()
if state.dimension == "--" and ctx.world then
state.dimension = tostring(ctx.world.dimension or "--")
end
if #state.points == 0 then
state.message = "Sin GPS: pulsa Centro e introduce coordenadas X Z."
end
end
 
function M.draw(ctx)
local width, height = term.getSize()
local layout = controls(width)
ui.button(layout.centerX, 3, layout.centerWidth,
width < 22 and "Coord" or "Centro", colors.green, colors.black)
ui.button(layout.minusX, 3, layout.zoomWidth, "-", colors.lightBlue)
ui.button(layout.plusX, 3, layout.zoomWidth, "+", colors.lightBlue)
ui.button(layout.destinationX, 3, layout.destinationWidth,
width < 22 and "Dst" or (layout.destinationWidth < 7 and "Dest." or "Destino"), colors.blue)
 
local mapTop = 5
local footer = height - 3
ui.text(2, 4, "N ^ " .. state.dimension:gsub("^minecraft:", "")
.. " X" .. math.floor(state.centerX) .. " Z" .. math.floor(state.centerZ),
colors.blue, colors.white, width - 2)
drawMap(width, mapTop, math.max(mapTop, footer - 1))
 
ui.text(2, footer, state.x and state.z
and string.format("@ X %.0f Z %.0f 1:%d", state.x, state.z, SCALES[state.zoom])
or string.format("Centro X %.0f Z %.0f 1:%d",
state.centerX, state.centerZ, SCALES[state.zoom]),
colors.black, colors.white, width - 2)
if state.destination and state.x and state.z then
local dx, dz = state.destination.x - state.x, state.destination.z - state.z
local distance = math.floor(math.sqrt(dx * dx + dz * dz) + 0.5)
ui.text(2, footer + 1, "Destino: " .. distance .. "m " .. direction(dx, dz),
colors.red, colors.white, width - 2)
ui.text(2, footer + 2, state.message, colors.gray, colors.white, width - 2)
elseif state.destination then
ui.text(2, footer + 1, "Destino X " .. math.floor(state.destination.x)
.. " Z " .. math.floor(state.destination.z), colors.red, colors.white, width - 2)
ui.text(2, footer + 2, state.message, colors.gray, colors.white, width - 2)
else
ui.text(2, footer + 1, "Toca el mapa para desplazarlo.", colors.gray, colors.white, width - 2)
end
end
 
function M.touch(ctx, x, y)
local width, height = term.getSize()
if y == 3 then
local layout = controls(width)
if x >= layout.centerX and x < layout.centerX + layout.centerWidth then
setCenter(ctx)
elseif x >= layout.minusX and x < layout.minusX + layout.zoomWidth then
state.zoom = math.max(1, state.zoom - 1)
local ok, err = save()
if not ok then state.message = "No se guardó el zoom: " .. tostring(err) end
elseif x >= layout.plusX and x < layout.plusX + layout.zoomWidth then
state.zoom = math.min(#SCALES, state.zoom + 1)
local ok, err = save()
if not ok then state.message = "No se guardó el zoom: " .. tostring(err) end
elseif x >= layout.destinationX
and x < layout.destinationX + layout.destinationWidth then
setDestination(ctx)
end
elseif y >= 5 and y <= height - 4 and x >= 3 and x < width - 1 then
local mapWidth = math.max(1, width - 4)
local mapHeight = math.max(1, height - 8)
local column = x - 2
local row = y - 4
local scale = SCALES[state.zoom]
state.centerX = state.centerX + (column - math.floor((mapWidth + 1) / 2)) * scale
state.centerZ = state.centerZ + (row - math.floor((mapHeight + 1) / 2)) * scale
state.dragX, state.dragY = x, y
state.message = "Mapa desplazado."
local ok, err = save()
if not ok then state.message = "No se guardo el mapa: " .. tostring(err) end
end
end
 
function M.drag(_, _, x, y)
if not state.dragX or not state.dragY then return end
local scale = SCALES[state.zoom]
state.centerX = state.centerX - (x - state.dragX) * scale
state.centerZ = state.centerZ - (y - state.dragY) * scale
state.dragX, state.dragY = x, y
state.message = "Mapa desplazado."
local ok, err = save()
if not ok then state.message = "No se guardo el mapa: " .. tostring(err) end
end
 
return M
]],
['youcube/youcube.lua'] = [=[
--[[
_   _ ____ _  _ ____ _  _ ___  ____
 \_/  |  | |  | |    |  | |__] |___
  |   |__| |__| |___ |__| |__] |___

Github Repository: https://github.com/Commandcracker/YouCube
License: GPL-3.0
]]

local _VERSION = "0.0.0-poc.1.1.2"

-- Libraries - OpenLibrarieLoader v1.0.1 --

--TODO: Optional libs:
-- For something like a JSON lib that is only needed for older CC Versions or
-- optional logging.lua support

local function is_lib(libs, lib)
    for i = 1, #libs do
        local value = libs[i]
        if value == lib or value .. ".lua" == lib then
            return true, value
        end
    end
    return false
end

local libs = { "youcubeapi", "numberformatter", "semver", "argparse", "string_pack" }
local lib_paths = { "/youcube", "/youcube/lib", ".", "./lib", "./apis", "./modules", "/", "/lib", "/apis", "/modules" }

-- LevelOS Support
if _G.lOS then
    lib_paths[#lib_paths + 1] = "/Program_Files/YouCube/lib"
end

local function load_lib(lib)
    return dofile(lib)
end

for i_path = 1, #lib_paths do
    local path = lib_paths[i_path]
    if fs.exists(path) then
        local files = fs.list(path)
        for i_file = 1, #files do
            local found, lib = is_lib(libs, files[i_file])
            if found and lib ~= nil and libs[lib] == nil then
                libs[lib] = load_lib(path .. "/" .. files[i_file])
            end
        end
    end
end

for i = 1, #libs do
    local lib = libs[i]
    if libs[lib] == nil then
        error(('Library "%s" not found.'):format(lib))
    end
end

-- args --

local function get_program_name()
    if arg then
        return arg[0]
    end
    return fs.getName(shell.getRunningProgram()):gsub("[\\.].*$", "")
end

-- stylua: ignore start

local parser = libs.argparse {
    help_max_width = ({ term.getSize() })[1],
    name = get_program_name()
}
    :description "Official YouCube client for accessing media from services like YouTube"

parser:argument "URL"
    :args "*"
    :description "URL or search term."

parser:flag "-v" "--verbose"
    :description "Enables verbose output."
    :target "verbose"
    :action "store_true"

parser:option "-V" "--volume"
    :description "Sets the volume of the audio. A value from 0-100"
    :target "volume"

parser:flag "--nv" "--no-video"
    :description "Disables video."
    :target "no_video"
    :action "store_true"

parser:flag "--na" "--no-audio"
    :description "Disables audio."
    :target "no_audio"
    :action "store_true"

parser:flag "--sh" "--shuffle"
    :description "Shuffles audio before playing"
    :target "shuffle"
    :action "store_true"

parser:flag "-l" "--loop"
    :description "Loops the media."
    :target "loop"
    :action "store_true"

parser:flag "--lp" "--loop-playlist"
    :description "Loops the playlist."
    :target "loop_playlist"
    :action "store_true"

parser:option "--fps"
    :description "Force sanjuuni to use a specified frame rate"
    :target "force_fps"

-- stylua: ignore end

local args = parser:parse({ ... })

if args.force_fps then
    args.force_fps = tonumber(args.force_fps)
end

if args.volume then
    args.volume = tonumber(args.volume)
    if args.volume == nil then
        parser:error("Volume must be a number")
    end

    if args.volume > 100 then
        parser:error("Volume cant be over 100")
    end

    if args.volume < 0 then
        parser:error("Volume cant be below 0")
    end
    args.volume = args.volume / 100
end

if #args.URL > 0 then
    args.URL = table.concat(args.URL, " ")
else
    args.URL = nil
end

if args.no_video and args.no_audio then
    parser:error("Nothing will happen, when audio and video is disabled!")
end

-- CraftOS-PC support --

if periphemu then
    periphemu.create("top", "speaker")
    -- Fuck the max websocket message police
    config.set("http_max_websocket_message", 2 ^ 30)
end

local function get_audiodevices()
    local audiodevices = {}

    local speakers = { peripheral.find("speaker") }
    for i = 1, #speakers do
        audiodevices[#audiodevices + 1] = libs.youcubeapi.Speaker.new(speakers[i])
    end

    local tapes = { peripheral.find("tape_drive") }
    for i = 1, #tapes do
        audiodevices[#audiodevices + 1] = libs.youcubeapi.Tape.new(tapes[i])
    end

    if #audiodevices == 0 then
        -- Disable audio when no audiodevice is found
        args.no_audio = true
        return audiodevices
    end

    -- Validate audiodevices
    local last_error
    local valid_audiodevices = {}

    for i = 1, #audiodevices do
        local audiodevice = audiodevices[i]
        local _error = audiodevice:validate()
        if _error == nil then
            valid_audiodevices[#valid_audiodevices + 1] = audiodevice
        else
            last_error = _error
        end
    end

    if #valid_audiodevices == 0 then
        args.no_audio = true
        printError("No se encontró un dispositivo de audio compatible: " .. tostring(last_error))
        return {}
    end

    return valid_audiodevices
end

-- main --

local youcubeapi = libs.youcubeapi.API.new()
local audiodevices = get_audiodevices()

-- update check --

local function get_versions()
    local url = "https://raw.githubusercontent.com/CC-YouCube/installer/main/versions.json"

    -- Check if the URL is valid
    local ok, err = http.checkURL(url)
    if not ok then
        printError("Invalid Update URL.", '"' .. url .. '" ', err)
        return
    end

    local response, http_err = http.get(url, nil, true)
    if not response then
        printError('Failed to retreat data from update URL. "' .. url .. '" (' .. http_err .. ")")
        return
    end

    local sResponse = response.readAll()
    response.close()

    return textutils.unserialiseJSON(sResponse)
end

local function write_colored(text, color)
    term.setTextColor(color)
    term.write(text)
end

local function new_line()
    local w, h = term.getSize()
    local x, y = term.getCursorPos()
    if y + 1 <= h then
        term.setCursorPos(1, y + 1)
    else
        term.setCursorPos(1, h)
        term.scroll(1)
    end
end

local function write_outdated(current, latest)
    if libs.semver(current) ^ libs.semver(latest) then
        term.setTextColor(colors.yellow)
    else
        term.setTextColor(colors.red)
    end

    term.write(current)
    write_colored(" -> ", colors.lightGray)
    write_colored(latest, colors.lime)
    term.setTextColor(colors.white)
    new_line()
end

local function can_update(name, current, latest)
    if libs.semver(current) < libs.semver(latest) then
        term.write(name .. " ")
        write_outdated(current, latest)
    end
end

local function update_checker()
    local versions = get_versions()
    if versions == nil then
        return
    end

    can_update("youcube", _VERSION, versions.client.version)
    can_update("youcubeapi", libs.youcubeapi._VERSION, versions.client.libraries.youcubeapi.version)
    can_update("numberformatter", libs.numberformatter._VERSION, versions.client.libraries.numberformatter.version)
    can_update("semver", tostring(libs.semver._VERSION), versions.client.libraries.semver.version)
    can_update("argparse", libs.argparse.version, versions.client.libraries.argparse.version)

    local handshake = youcubeapi:handshake()

    if libs.semver(handshake.server.version) < libs.semver(versions.server.version) then
        print("Tell the server owner to update their server!")
        write_outdated(handshake.server.version, versions.server.version)
    end

    if not libs.semver(libs.youcubeapi._API_VERSION) ^ libs.semver(handshake.api.version) then
        print("Client is not compatible with server")
        write_colored(libs.youcubeapi._API_VERSION, colors.red)
        write_colored(" ^ ", colors.lightGray)
        write_colored(handshake.api.version, colors.red)
        term.setTextColor(colors.white)
        new_line()
    end

    if libs.semver(libs.youcubeapi._API_VERSION) < libs.semver(versions.api.version) then
        print("Your client is using an outdated API version")
        write_outdated(libs.youcubeapi._API_VERSION, versions.api.version)
    end

    if libs.semver(handshake.api.version) < libs.semver(versions.api.version) then
        print("The server is using an outdated API version")
        write_outdated(libs.youcubeapi._API_VERSION, versions.api.version)
    end
end

local function play_audio(buffer, title)
    for i = 1, #audiodevices do
        local audiodevice = audiodevices[i]
        audiodevice:reset()
        audiodevice:setLabel(title)
        audiodevice:setVolume(args.volume)
    end

    while true do
        local chunk = buffer:next()

        -- Adjust buffer size on first chunk
        if buffer.filler.chunkindex == 1 then
            buffer.size = math.ceil(1024 / (#chunk / 16))
        end

        if chunk == "" then
            local play_functions = {}
            for i = 1, #audiodevices do
                local audiodevice = audiodevices[i]
                play_functions[#play_functions + 1] = function()
                    audiodevice:play()
                end
            end

            parallel.waitForAll(table.unpack(play_functions))
            return
        end

        local write_functions = {}
        for i = 1, #audiodevices do
            local audiodevice = audiodevices[i]
            table.insert(write_functions, function()
                audiodevice:write(chunk)
            end)
        end

        parallel.waitForAll(table.unpack(write_functions))
    end
end

-- #region playback controll vars
local back_buffer = {}
local max_back = settings.get("youcube.max_back") or 32
local queue = {}
local restart = false
-- #endregion

-- keys
local skip_key = settings.get("youcube.keys.skip") or keys.d
local restart_key = settings.get("youcube.keys.restart") or keys.r
local back_key = settings.get("youcube.keys.back") or keys.a

local function play(url)
    restart = false
    print("Requesting media ...")

    if not args.no_video then
        youcubeapi:request_media(url, term.getSize())
    else
        youcubeapi:request_media(url)
    end

    local data
    local x, y = term.getCursorPos()

    repeat
        data = youcubeapi:receive()
        if data.action == "status" then
            os.queueEvent("youcube:status", data)
            term.setCursorPos(x, y)
            term.clearLine()
            term.write("Status: ")
            write_colored(data.message, colors.green)
            term.setTextColor(colors.white)
        elseif data.action == "error" then
            error(data.message or "The YouCube server returned an error")
        else
            new_line()
        end
    until data.action == "media"

    term.write("Playing: ")
    term.setTextColor(colors.lime)
    print(data.title)
    term.setTextColor(colors.white)

    if data.like_count then
        print("Likes: " .. libs.numberformatter.compact(data.like_count))
    end

    if data.view_count then
        print("Views: " .. libs.numberformatter.compact(data.view_count))
    end

    local video_buffer = libs.youcubeapi.Buffer.new(
        libs.youcubeapi.VideoFiller.new(youcubeapi, data.id, term.getSize()),
        60 -- Most videos run on 30 fps, so we store 2s of video.
    )

    local audio_buffer = libs.youcubeapi.Buffer.new(
        libs.youcubeapi.AudioFiller.new(youcubeapi, data.id),
        --[[
            We want to buffer 1024 chunks.
            One chunks is 16 bits.
            The server (with default settings) sends 32 chunks at once.
        ]]
        32
    )

    if args.verbose then
        term.clear()
        term.setCursorPos(1, 1)
        term.write("[DEBUG MODE]")
    end

    local function fill_buffers()
        while true do
            os.queueEvent("youcube:fill_buffers")

            local event = os.pullEventRaw()

            if event == "terminate" then
                libs.youcubeapi.reset_term()
            end

            if not args.no_audio then
                audio_buffer:fill()
            end

            if args.verbose then
                term.setCursorPos(1, ({ term.getSize() })[2])
                term.clearLine()
                term.write("Audio_Buffer: " .. #audio_buffer.buffer)
            end

            if not args.no_video then
                video_buffer:fill()
            end
        end
    end

    local function _play_video()
        if not args.no_video then
            local string_unpack
            if not string.unpack then
                string_unpack = libs.string_pack.unpack
            end

            os.queueEvent("youcube:vid_playing", data)
            libs.youcubeapi.play_vid(video_buffer, args.force_fps, string_unpack,
                youcubeapi.server == "wss://youcube.onrender.com")
            os.queueEvent("youcube:vid_eof", data)
        end
    end

    local function _play_audio()
        if not args.no_audio then
            os.queueEvent("youcube:audio_playing", data)
            play_audio(audio_buffer, data.title)
            os.queueEvent("youcube:audio_eof", data)
        end
    end

    local function _play_media()
        os.queueEvent("youcube:playing")
        parallel.waitForAll(_play_video, _play_audio)
    end

    local function _hotkey_handler()
        while true do
            local _, key = os.pullEvent("key")

            if key == skip_key then
                back_buffer[#back_buffer + 1] = url --finished playing, push the value to the back buffer
                if #back_buffer > max_back then
                    back_buffer[1] = nil --remove it from the front of the buffer
                end
                if not args.no_video then
                    libs.youcubeapi.reset_term()
                end
                break
            end

            if key == restart_key then
                queue[#queue + 1] = url --add the current song to upcoming
                if not args.no_video then
                    libs.youcubeapi.reset_term()
                end
                restart = true
                break
            end
        end
    end

    parallel.waitForAny(fill_buffers, _play_media, _hotkey_handler)

    if data.playlist_videos then
        return data.playlist_videos
    end
end

local function shuffle_playlist(playlist)
    local shuffled = {}
    for i = 1, #queue do
        local pos = math.random(1, #shuffled + 1)
        shuffled[pos] = queue[i]
    end
    return shuffled
end

local function play_playlist(playlist)
    queue = playlist
    if args.shuffle then
        queue = shuffle_playlist(queue)
    end
    while #queue ~= 0 do
        local pl = table.remove(queue)

        local function handle_back_hotkey()
            while true do
                local _, key = os.pullEvent("key")
                if key == back_key then
                    queue[#queue + 1] = pl --add the current song to upcoming
                    local prev = table.remove(back_buffer)
                    if prev then --nil/false check
                        queue[#queue + 1] = prev --add previous song to upcoming
                    end
                    if not args.no_video then
                        libs.youcubeapi.reset_term()
                    end
                    break
                end
            end
        end

        parallel.waitForAny(handle_back_hotkey, function()
            play(pl) --play the url
        end)
    end
end

local function main()
    youcubeapi:detect_bestest_server(nil, args.verbose)
    -- Skip the optional update check: it performs an extra HTTP request and
    -- websocket handshake before playback, which is costly on pocket computers.

    if not args.URL then
        print("Enter Url or Search Term:")
        term.setTextColor(colors.lightGray)
        args.URL = read()
        term.setTextColor(colors.white)
    end

    local playlist_videos = play(args.URL)

    if args.loop == true then
        while true do
            play(args.URL)
        end
    end
    if playlist_videos then
        if args.loop_playlist == true then
            while true do
                if playlist_videos then
                    play_playlist(playlist_videos)
                end
            end
        end
        play_playlist(playlist_videos)
    end

    while restart do
        play(args.URL)
    end

    youcubeapi.websocket.close()

    if not args.no_video then
        libs.youcubeapi.reset_term()
    end

    os.queueEvent("youcube:playback_ended")
end

main()
]=],
['youcube/lib/youcubeapi.lua'] = [=[
--[[- Lua library for accessing [YouCub's API](https://commandcracker.github.io/YouCube/)
    @module youcubeapi
]]

--[[ youcubeapi.lua
_   _ ____ _  _ ____ _  _ ___  ____ ____ ___  _
 \_/  |  | |  | |    |  | |__] |___ |__| |__] |
  |   |__| |__| |___ |__| |__] |___ |  | |    |
]]

--[[- "wrapper" for accessing [YouCub's API](https://commandcracker.github.io/YouCube/)
    @type API
    @usage Example:

        local youcubeapi  = require("youcubeapi")
        local api         = youcubeapi.API.new()
        api:detect_bestest_server()
        api:request_media(url)
        local data = api.websocket.receive()
]]
local API = {}

--- Create's a new API instance.
-- @param websocket [Websocket](https://tweaked.cc/module/http.html#ty:Websocket) The websocket.
-- @treturn API instance
function API.new(websocket)
    return setmetatable({
        websocket = websocket,
    }, { __index = API })
end

-- Look at the [Documentation](https://commandcracker.github.io/YouCube/) for moor information
-- Contact the server owner on Discord, when the server is down
local servers = {
    "wss://youcube.knijn.one", -- Primary server, hosted by EmmaKnijn
    "wss://youcube.onrender.com", -- By Commandcracker#8528
}

local function websocket_with_timeout(_url, _headers, _timeout)
    if http.websocketAsync then
        local websocket, websocket_error = http.websocketAsync(_url, _headers)
        if not websocket then
            return false, websocket_error
        end

        local timerID = os.startTimer(_timeout)

        while true do
            local event, param1, param2 = os.pullEvent()

            -- TODO: Close web-socket when the connection succeeds after the timeout
            if event == "websocket_success" and param1 == _url then
                return param2
            elseif event == "websocket_failure" and param1 == _url then
                return false, param2
            elseif event == "timer" and param1 == timerID then
                return false, "Timeout"
            end
        end
    end

    -- use websocket without timeout
    -- when the CC version dos not support websocketAsync
    return http.websocket(_url, _headers)
end

--- Connects to a YouCub Server
function API:detect_bestest_server(_server, _verbose)
    for i = 1, #servers do
        local server = servers[i]
        local ok, err = http.checkURL(server:gsub("^ws://", "http://"):gsub("^wss://", "https://"))

        if ok then
            if _verbose then
                print("Trying to connect to:", server)
            end
            local websocket, websocket_error = websocket_with_timeout(server, nil, 5)

            if websocket ~= false then
                term.write("Using the YouCube server: ")
                term.setTextColor(colors.blue)
                print(server)
                term.setTextColor(colors.white)
                self.websocket = websocket
                self.server = server
                break
            elseif i == #servers then
                error(websocket_error)
            elseif _verbose then
                print(websocket_error)
            end
        elseif i == #servers then
            error(err)
        elseif _verbose then
            print(err)
        end
    end
end

--- Receive data from The YouCub Server
-- @tparam string filter action filter
-- @treturn table retval data
function API:receive(filter)
    local status, retval = pcall(self.websocket.receive)
    if not status then
        error("Lost connection to server\n" .. retval)
    end

    if retval == nil then
        error("Received empty message or max message size exceeded")
    end

    local data, err = textutils.unserialiseJSON(retval)

    if data == nil then
        error("Failed to parse message\n" .. err)
    end

    if filter then
        --if type(filter) == "table" then
        --    if not filter[data.action] then
        --        return self:receive(filter)
        --    end
        --else
        if data.action ~= filter then
            return self:receive(filter)
        end
    end

    return data
end

--- Send data to The YouCub Server
-- @tparam table data data to send
function API:send(data)
    local status, retval = pcall(self.websocket.send, textutils.serialiseJSON(data))
    if not status then
        error("Lost connection to server\n" .. retval)
    end
end

--[[- [Base64](https://wikipedia.org/wiki/Base64) functions
    @type Base64
]]
local Base64 = {}

local b64str = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

-- based on https://github.com/MCJack123/sanjuuni/blob/c64f8725a9f24dec656819923457717dfb964515/raw-player.lua
--- Decode base64 string
-- @tparam string str base64 string
-- @treturn string string decoded string
function Base64.decode(str)
    local retval = ""
    for s in str:gmatch("....") do
        if s:sub(3, 4) == "==" then
            retval = retval
                .. string.char(
                    bit32.bor(
                        bit32.lshift(b64str:find(s:sub(1, 1)) - 1, 2),
                        bit32.rshift(b64str:find(s:sub(2, 2)) - 1, 4)
                    )
                )
        elseif s:sub(4, 4) == "=" then
            local n = (b64str:find(s:sub(1, 1)) - 1) * 4096
                + (b64str:find(s:sub(2, 2)) - 1) * 64
                + (b64str:find(s:sub(3, 3)) - 1)
            retval = retval .. string.char(bit32.extract(n, 10, 8)) .. string.char(bit32.extract(n, 2, 8))
        else
            local n = (b64str:find(s:sub(1, 1)) - 1) * 262144
                + (b64str:find(s:sub(2, 2)) - 1) * 4096
                + (b64str:find(s:sub(3, 3)) - 1) * 64
                + (b64str:find(s:sub(4, 4)) - 1)
            retval = retval
                .. string.char(bit32.extract(n, 16, 8))
                .. string.char(bit32.extract(n, 8, 8))
                .. string.char(bit32.extract(n, 0, 8))
        end
    end
    return retval
end

--- Request a `16 * 1024` bit chunk
-- @tparam number chunkindex The chunkindex
-- @tparam string id Media id
-- @treturn bytes chunk `16 * 1024` bit chunk
function API:get_chunk(chunkindex, id)
    self:send({
        ["action"] = "get_chunk",
        ["chunkindex"] = chunkindex,
        ["id"] = id,
    })
    return Base64.decode(self:receive("chunk").chunk)
end

--- Get 32vid
-- @tparam number line The line to return
-- @tparam string id Media id
-- @tparam number width Video width
-- @tparam number height Video height
-- @treturn string line one line of the given 32vid
function API:get_vid(tracker, id, width, height)
    self:send({
        ["action"] = "get_vid",
        ["tracker"] = tracker,
        ["id"] = id,
        ["width"] = width * 2,
        ["height"] = height * 3,
    })
    return self:receive("vid")
end

--- Request media
-- @tparam string url Url or Search Term
--@treturn table json response
function API:request_media(url, width, height)
    local request = {
        ["action"] = "request_media",
        ["url"] = url,
    }
    if width and height then
        request.width = width * 2
        request.height = height * 3
    end
    self:send(request)
    --return self:receive({ ["media"] = true, ["status"] = true })
end

--- Handshake - get Server capabilities and version
--@treturn table json response
function API:handshake()
    self:send({
        ["action"] = "handshake",
    })
    return self:receive("handshake")
end

--[[- Abstraction for Audio Devices
    @type AudioDevice
]]
local AudioDevice = {}

--- Create's a new AudioDevice instance.
-- @tparam table object Base values
-- @treturn AudioDevice instance
function AudioDevice.new(object)
    -- @type AudioDevice
    local self = object or {}

    function self:validate() end

    function self:setLabel(lable) end

    function self:write(chunk) end

    function self:play() end

    function self:reset() end

    function self:setVolume(volume) end

    return self
end

--[[- @{AudioDevice} from a Speaker
    @type Speaker
    @usage Example:

        local youcubeapi  = require("youcubeapi")
        local speaker     = peripheral.find("speaker")
        local audiodevice = youcubeapi.Speaker.new(speaker)
]]
local Speaker = {}

local decoder
local status, dfpwm = pcall(require, "cc.audio.dfpwm")

if status then
    decoder = dfpwm.make_decoder()
end

--- Create's a new Tape instance.
-- @tparam speaker speaker The speaker
-- @treturn AudioDevice|Speaker instance
function Speaker.new(speaker)
    local self = AudioDevice.new({ speaker = speaker })

    function self:validate()
        if not decoder then
            return "This ComputerCraft version dos not support DFPWM"
        end
    end

    function self:setVolume(volume)
        self.volume = volume
    end

    function self:write(chunk)
        local buffer = decoder(chunk)
        while not self.speaker.playAudio(buffer, self.volume) do
            os.pullEvent("speaker_audio_empty")
        end
    end

    return self
end

--[[- @{AudioDevice} from a [Computronics tape_drive](https://wiki.vexatos.com/wiki:computronics:tape)
    @type Tape
    @usage Example:

        local youcubeapi  = require("youcubeapi")
        local tape_drive  = peripheral.find("tape_drive")
        local audiodevice = youcubeapi.Tape.new(tape_drive)
]]
local Tape = {}

--- Create's a new Tape instance.
-- @tparam tape tape The tape_drive
-- @treturn AudioDevice|Tape instance
function Tape.new(tape)
    local self = AudioDevice.new({ tape = tape })

    function self:validate()
        if not self.tape.isReady() then
            return "You need to insert a tape"
        end
    end

    function self:setVolume(volume)
        if volume then
            self.tape.setVolume(volume)
        end
    end

    function self:play(chunk)
        self.tape.seek(-self.tape.getSize())
        self.tape.play()
    end

    function self:write(chunk)
        self.tape.write(chunk)
    end

    function self:setLabel(lable)
        self.tape.setLabel(lable)
    end

    function self:reset()
        -- based on https://github.com/Vexatos/Computronics/blob/b0ade53cab10529dbe91ebabfa882d1b4b21fa90/src/main/resources/assets/computronics/lua/peripheral/tape_drive/programs/tape_drive/tape#L109-L123
        local size = self.tape.getSize()
        self.tape.stop()
        self.tape.seek(-size)
        self.tape.stop()
        self.tape.seek(-90000)
        local s = string.rep(string.char(170), 8192)
        for i = 1, size + 8191, 8192 do
            self.tape.write(s)
        end
        self.tape.seek(-size)
        self.tape.seek(-90000)
    end

    return self
end

--[[- Abstract object for filling a @{Buffer}
    @type Filler
]]
local Filler = {}

--- Create's a new Filler instance.
-- @treturn Filler instance
function Filler.new()
    local self = {}
    function self:next() end

    return self
end

--[[- @{Filler} for Audio
    @type AudioFiller
]]
local AudioFiller = {}

--- Create's a new AudioFiller instance.
-- @tparam API youcubeapi API object
-- @tparam string id Media id
-- @treturn AudioFiller|Filler instance
function AudioFiller.new(youcubeapi, id)
    local self = {
        id = id,
        chunkindex = 0,
        youcubeapi = youcubeapi,
    }

    function self:next()
        local response = self.youcubeapi:get_chunk(self.chunkindex, self.id)
        self.chunkindex = self.chunkindex + 1
        return response
    end

    return self
end

--[[- @{Filler} for Video
    @type VideoFiller
]]
local VideoFiller = {}

--- Create's a new VideoFiller instance.
-- @tparam API youcubeapi API object
-- @tparam string id Media id
-- @tparam number width Video width
-- @tparam number height Video height
-- @treturn VideoFiller|Filler instance
function VideoFiller.new(youcubeapi, id, width, height)
    local self = {
        id = id,
        width = width,
        height = height,
        tracker = 0,
        youcubeapi = youcubeapi,
    }

    function self:next()
        local response = self.youcubeapi:get_vid(self.tracker, self.id, self.width, self.height)
        for i = 1, #response.lines do
            self.tracker = self.tracker + #response.lines[i] + 1
        end
        return response.lines
    end

    return self
end

--[[- Buffers Data
    @type Buffer
]]
local Buffer = {}

--- Create's a new Buffer instance.
-- @tparam Filler filler filler instance
-- @tparam number size buffer limit
-- @treturn Buffer instance
function Buffer.new(filler, size)
    local self = {
        filler = filler,
        size = size,
    }
    self.buffer = {}

    function self:next()
        while #self.buffer == 0 do
            os.pullEvent()
        end -- Wait until next is available
        local next = self.buffer[1]
        table.remove(self.buffer, 1)
        return next
    end

    function self:fill()
        if #self.buffer < self.size then
            local next = filler:next()
            if type(next) == "table" then
                for i = 1, #next do
                    self.buffer[#self.buffer + 1] = next[i]
                end
            else
                self.buffer[#self.buffer + 1] = next
            end
            return true
        end
        return false
    end

    return self
end

local currnt_palette = {}

for i = 0, 15 do
    local r, g, b = term.getPaletteColour(2 ^ i)
    currnt_palette[i] = { r, g, b }
end

local function reset_term()
    for i = 0, 15 do
        term.setPaletteColor(2 ^ i, currnt_palette[i][1], currnt_palette[i][2], currnt_palette[i][3])
    end
    term.setBackgroundColor(colors.black)
    term.setTextColor(colors.white)
    term.clear()
    term.setCursorPos(1, 1)
end

--[[- Create's a new Buffer instance.

    Based on [sanjuuni/raw-player.lua](https://github.com/MCJack123/sanjuuni/blob/c64f8725a9f24dec656819923457717dfb964515/raw-player.lua)
    and [sanjuuni/websocket-player.lua](https://github.com/MCJack123/sanjuuni/blob/30dcabb4b56f1eb32c88e1bce384b0898367ebda/websocket-player.lua)
    @tparam Buffer buffer filled with frames
]]
local function play_vid(buffer, force_fps, string_unpack, auto_advance)
    if not string_unpack then
        string_unpack = string.unpack
    end
    local Fwidth, Fheight = term.getSize()
    local tracker = 0

    if buffer:next() ~= "32Vid 1.1" then
        error("Unsupported file")
    end

    local fps = tonumber(buffer:next())
    if force_fps then
        fps = force_fps
    end

    -- Adjust buffer size
    buffer.size = math.ceil(fps) * 2

    local first, second = buffer:next(), buffer:next()

    if second == "" or second == nil then
        fps = 0
    end
    term.clear()

    local start = os.epoch("utc")
    local frame_count = 0
    while true do
        frame_count = frame_count + 1
        local frame
        if first then
            frame, first = first, nil
        elseif second then
            frame, second = second, nil
        else
            frame = buffer:next()
        end
        if frame == "" or frame == nil then
            break
        end
        local mode = frame:match("^!CP([CD])")
        if not mode then
            error("Invalid file")
        end
        local b64data
        if mode == "C" then
            local len = tonumber(frame:sub(5, 8), 16)
            b64data = frame:sub(9, len + 8)
        else
            local len = tonumber(frame:sub(5, 16), 16)
            b64data = frame:sub(17, len + 16)
        end
        local data = Base64.decode(b64data)
        -- TODO: maybe verify checksums?
        assert(data:sub(1, 4) == "\0\0\0\0" and data:sub(9, 16) == "\0\0\0\0\0\0\0\0", "Invalid file")
        local width, height = string_unpack("HH", data, 5)
        local c, n, pos = string_unpack("c1B", data, 17)
        local text = {}
        for y = 1, height do
            text[y] = ""
            for x = 1, width do
                text[y] = text[y] .. c
                n = n - 1
                if n == 0 then
                    c, n, pos = string_unpack("c1B", data, pos)
                end
            end
        end
        c = c:byte()
        for y = 1, height do
            local fg, bg = "", ""
            for x = 1, width do
                fg, bg = fg .. ("%x"):format(bit32.band(c, 0x0F)), bg .. ("%x"):format(bit32.rshift(c, 4))
                n = n - 1
                if n == 0 then
                    c, n, pos = string_unpack("BB", data, pos)
                end
            end
            term.setCursorPos(1, y)
            term.blit(text[y], fg, bg)
        end
        pos = pos - 2
        local r, g, b
        for i = 0, 15 do
            r, g, b, pos = string_unpack("BBB", data, pos)
            term.setPaletteColor(2 ^ i, r / 255, g / 255, b / 255)
        end
        if fps == 0 then
            if not auto_advance then read() end
            break
        else
            while os.epoch("utc") < start + (frame_count + 1) / fps * 1000 do
                sleep(1 / fps)
            end
        end
    end
    reset_term()
end

return {
    --- "Metadata" - [YouCube API](https://commandcracker.github.io/YouCube/) Version
    _API_VERSION = "0.0.0-poc.1.0.0",
    --- "Metadata" - Library Version
    _VERSION = "0.0.0-poc.1.4.2",
    --- "Metadata" - Description
    _DESCRIPTION = "Library for accessing YouCub's API",
    --- "Metadata" - Homepage / Url
    _URL = "https://github.com/Commandcracker/YouCube",
    --- "Metadata" - License
    _LICENSE = "GPL-3.0",
    API = API,
    AudioDevice = AudioDevice,
    Speaker = Speaker,
    Tape = Tape,
    Base64 = Base64,
    Filler = Filler,
    AudioFiller = AudioFiller,
    VideoFiller = VideoFiller,
    Buffer = Buffer,
    play_vid = play_vid,
    reset_term = reset_term,
}
]=],
['youcube/lib/numberformatter.lua'] = [=[
--[[- Library for formatting numbers
    @module numberformatter
]]

--[[ numberformatter.lua
_  _ _  _ _  _ ___  ____ ____
|\ | |  | |\/| |__] |___ |__/
| \| |__| |  | |__] |___ |  \
____ ____ ____ _  _ ____ ___ ___ ____ ____
|___ |  | |__/ |\/| |__|  |   |  |___ |__/
|    |__| |  \ |  | |  |  |   |  |___ |  \
]]

local NumberFormatter = {
    --- "Metadata" - Version
    _VERSION     = "1.1.0",
    --- "Metadata" - Description
    _DESCRIPTION = "Library for formatting numbers",
    --- "Metadata" - Homepage / Url
    _URL         = "https://github.com/Commandcracker/YouCube",
    --- "Metadata" - License
    _LICENSE     = "GPL-3.0"
}
--[[
    NumberFormatter.compact and NumberFormatter.abbreviate based on:
    https://devforum.roblox.com/t/how-can-i-turn-a-number-to-a-shorter-number-i-dont-know-how-to-explain-click-to-understand-3/649496/3
]]

local Suffixes = { "k", "M", "B", "T", "qd", "Qn", "sx", "Sp", "O", "N", "de", "Ud", "DD", "tdD", "qdD", "QnD", "sxD",
    "SpD", "OcD", "NvD", "Vgn", "UVg", "DVg", "TVg", "qtV", "QnV", "SeV", "SPG", "OVG", "NVG", "TGN", "UTG", "DTG",
    "tsTG", "qtTG", "QnTG", "ssTG", "SpTG", "OcTG", "NoTG", "QdDR", "uQDR", "dQDR", "tQDR", "qdQDR", "QnQDR", "sxQDR",
    "SpQDR", "OQDDr", "NQDDr", "qQGNT", "uQGNT", "dQGNT", "tQGNT", "qdQGNT", "QnQGNT", "sxQGNT", "SpQGNT", "OQQGNT",
    "NQQGNT", "SXGNTL" }

--[[- Format number by LDML's specification for [Compact Number Formats](http://unicode.org/reports/tr35/tr35-numbers.html#Compact_Number_Formats)
    @tparam number number The number to format
    @treturn string formatted number
    @usage Example:

        local numberformatter = require("numberformatter")
        print(numberformatter.compact(1000))
    
    Output: `1k`
]]
function NumberFormatter.compact(number)
    local Negative = number < 0
    number = math.abs(number)

    local Paired = false
    for i in pairs(Suffixes) do
        if not (number >= 10 ^ (3 * i)) then
            number = number / 10 ^ (3 * (i - 1))
            local isComplex = string.find(tostring(number), ".") and string.sub(tostring(number), 4, 4) ~= "."
            number = string.sub(tostring(number), 1, isComplex and 4 or 3) .. (Suffixes[i - 1] or "")
            Paired = true
            break
        end
    end
    if not Paired then
        local Rounded = math.floor(number)
        number = tostring(Rounded)
    end
    if Negative then
        return "-" .. number
    end
    return number -- returns 1.0k for example
end

--[[- Format number - separate thousands by comma
    @tparam number number The number to format
    @treturn string formatted number
    @usage Example:

        local numberformatter = require("numberformatter")
        print(numberformatter.abbreviate(1000))
    
    Output: `1,000`
]]
function NumberFormatter.abbreviate(number)
    local left, num, right = string.match(number, '^([^%d]*%d)(%d*)(.-)$')
    return left .. num:reverse():gsub('(%d%d%d)', '%1,'):reverse() .. right
end

return NumberFormatter
]=],
['youcube/lib/semver.lua'] = [=[
local
e={_VERSION='1.2.1',_DESCRIPTION='semver for Lua',_URL='https://github.com/kikito/semver.lua',_LICENSE=[[
      MIT LICENSE
  
      Copyright (c) 2015 Enrique García Cota
  
      Permission is hereby granted, free of charge, to any person obtaining a
      copy of tother software and associated documentation files (the
      "Software"), to deal in the Software without restriction, including
      without limitation the rights to use, copy, modify, merge, publish,
      distribute, sublicense, and/or sell copies of the Software, and to
      permit persons to whom the Software is furnished to do so, subject to
      the following conditions:
  
      The above copyright notice and tother permission notice shall be included
      in all copies or substantial portions of the Software.
  
      THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
      OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
      MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
      IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
      CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
      TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
      SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
    ]]}local
function
t(a,o)assert(a>=0,o..' must be a valid positive number')assert(math.floor(a)==a,o..' must be an integer')end
local function i(n)return n and n~=''end local function s(h)h=h or""local
r,d={},0 h:gsub("([^%.]+)",function(l)d=d+1 r[d]=l end)return r end local
function u(c)local m,f=c:match("^(-[^+]+)(+.+)$")if not(m and f)then
m=c:match("^(-.+)$")f=c:match("^(+.+)$")end assert(m or
f,("The parameter %q must begin with + or - to denote a prerelease or a build"):format(c))return
m,f end local function w(y)if y then local
p=y:match("^-(%w[%.%w-]*)$")assert(p,("The prerelease %q is not a slash followed by alphanumerics, dots and slashes"):format(y))return
p end end local function v(b)if b then local
g=b:match("^%+(%w[%.%w-]*)$")assert(g,("The build %q is not a + sign followed by alphanumerics, dots and slashes"):format(b))return
g end end local function k(q)if not i(q)then return nil,nil end local
j,x=u(q)local z=w(j)local E=v(x)return z,E end local function T(A)local
O,I,N,S=A:match("^(%d+)%.?(%d*)%.?(%d*)(.-)$")assert(type(O)=='string',("Could not extract version number(s) from %q"):format(A))local
H,R,D=tonumber(O),tonumber(I),tonumber(N)local L,U=k(S)return H,R,D,L,U end
local function C(M,F)return M==F and 0 or M<F and-1 or 1 end local function
W(Y,P)if Y==P then return 0 elseif not Y then return-1 elseif not P then return
1 end local V,B=tonumber(Y),tonumber(P)if V and B then return C(V,B)elseif V
then return-1 elseif B then return 1 else return C(Y,P)end end local function
G(K,Q)local J=#K local X for Z=1,J do X=W(K[Z],Q[Z])if X~=0 then return X==-1
end end return J<#Q end local function et(tt,at)if tt==at or not tt then return
false elseif not at then return true end return G(s(tt),s(at))end local
ot={}function ot:nextMajor()return e(self.major+1,0,0)end function
ot:nextMinor()return e(self.major,self.minor+1,0)end function
ot:nextPatch()return e(self.major,self.minor,self.patch+1)end local
it={__index=ot}function it:__eq(nt)return self.major==nt.major and
self.minor==nt.minor and self.patch==nt.patch and
self.prerelease==nt.prerelease end function it:__lt(st)if self.major~=st.major
then return self.major<st.major end if self.minor~=st.minor then return
self.minor<st.minor end if self.patch~=st.patch then return self.patch<st.patch
end return et(self.prerelease,st.prerelease)end function it:__pow(ht)if
self.major==0 then return self==ht end return self.major==ht.major and
self.minor<=ht.minor end function it:__tostring()local
rt={("%d.%d.%d"):format(self.major,self.minor,self.patch)}if self.prerelease
then table.insert(rt,"-"..self.prerelease)end if self.build then
table.insert(rt,"+"..self.build)end return table.concat(rt)end local function
dt(lt,ut,ct,mt,ft)assert(lt,"At least one parameter is needed")if
type(lt)=='string'then lt,ut,ct,mt,ft=T(lt)end ct=ct or 0 ut=ut or 0
t(lt,"major")t(ut,"minor")t(ct,"patch")local
wt={major=lt,minor=ut,patch=ct,prerelease=mt,build=ft}return
setmetatable(wt,it)end setmetatable(e,{__call=function(yt,...)return
dt(...)end})e._VERSION=e(e._VERSION)return
e
]=],
['youcube/lib/argparse.lua'] = [==[
local function e(t,a)for o,i in pairs(a)do if type(i)=="table"then i=e({},i)end
t[o]=i end return t end local function n(s,h,r)local d={}d.__index=d if r then
d.__prototype=e(e({},r.__prototype),s)else d.__prototype=s end if h then local
l={}for u,c in ipairs(h)do local m,f=c[1],c[2]d[m]=function(w,y)if not
f(w,y)then w["_"..m]=y end return w end l[m]=true end function
d.__call(p,...)if type((...))=="table"then for v,b in pairs((...))do if
l[v]then p[v](p,b)end end else local g=select("#",...)for k,q in ipairs(h)do if
k>g or k>h.args then break end local arg=select(k,...)if arg~=nil then
p[q[1]](p,arg)end end end return p end end local j={}j.__index=r function
j.__call(x,...)local z=e({},x.__prototype)setmetatable(z,x)return z(...)end
return setmetatable(d,j)end local function E(T,A,O)for I,N in ipairs(A)do if
type(O)==N then return true end end
error(("bad property '%s' (%s expected, got %s)"):format(T,table.concat(A," or "),type(O)))end
local function S(H,...)local R={...}return{H,function(D,L)E(H,R,L)end}end local
U={"name",function(C,M)E("name",{"string"},M)for F in M:gmatch("%S+")do
C._name=C._name or F
table.insert(C._aliases,F)table.insert(C._public_aliases,F)if
F:find("_",1,true)then table.insert(C._aliases,(F:gsub("_","-")))end end return
true end}local W={"hidden_name",function(Y,P)E("hidden_name",{"string"},P)for V
in P:gmatch("%S+")do table.insert(Y._aliases,V)if V:find("_",1,true)then
table.insert(Y._aliases,(V:gsub("_","-")))end end return true end}local
function B(G)if tonumber(G)then return tonumber(G),tonumber(G)end if G=="*"then
return 0,math.huge end if G=="+"then return 1,math.huge end if G=="?"then
return 0,1 end if G:match"^%d+%-%d+$"then local
K,Q=G:match"^(%d+)%-(%d+)$"return tonumber(K),tonumber(Q)end if
G:match"^%d+%+$"then local J=G:match"^(%d+)%+$"return tonumber(J),math.huge end
end local function X(Z)return{Z,function(et,tt)E(Z,{"number","string"},tt)local
at,ot=B(tt)if not at then error(("bad property '%s'"):format(Z))end
et["_min"..Z],et["_max"..Z]=at,ot end}end local it={}local
nt={"action",function(st,ht)E("action",{"function","string"},ht)if
type(ht)=="string"and not it[ht]then
error(("unknown action '%s'"):format(ht))end end}local
rt={"init",function(dt)dt._has_init=true end}local
lt={"default",function(ut,ct)if type(ct)~="string"then ut._init=ct
ut._has_init=true return true end end}local
mt={"add_help",function(ft,wt)E("add_help",{"boolean","string","table"},wt)if
ft._help_option_idx then
table.remove(ft._options,ft._help_option_idx)ft._help_option_idx=nil end if wt
then local
yt=ft:flag():description"Show this help message and exit.":action(function()print(ft:get_help())error()end)if
wt~=true then yt=yt(wt)end if not yt._name then yt"-h""--help"end
ft._help_option_idx=#ft._options end end}local
pt=n({_arguments={},_options={},_commands={},_mutexes={},_groups={},_require_command=true,_handle_options=true},{args=3,S("name","string"),S("description","string"),S("epilog","string"),S("usage","string"),S("help","string"),S("require_command","boolean"),S("handle_options","boolean"),S("action","function"),S("command_target","string"),S("help_vertical_space","number"),S("usage_margin","number"),S("usage_max_width","number"),S("help_usage_margin","number"),S("help_description_margin","number"),S("help_max_width","number"),mt})local
vt=n({_aliases={},_public_aliases={}},{args=3,U,S("description","string"),S("epilog","string"),W,S("summary","string"),S("target","string"),S("usage","string"),S("help","string"),S("require_command","boolean"),S("handle_options","boolean"),S("action","function"),S("command_target","string"),S("help_vertical_space","number"),S("usage_margin","number"),S("usage_max_width","number"),S("help_usage_margin","number"),S("help_description_margin","number"),S("help_max_width","number"),S("hidden","boolean"),mt},pt)local
bt=n({_minargs=1,_maxargs=1,_mincount=1,_maxcount=1,_defmode="unused",_show_default=true},{args=5,S("name","string"),S("description","string"),lt,S("convert","function","table"),X("args"),S("target","string"),S("defmode","string"),S("show_default","boolean"),S("argname","string","table"),S("choices","table"),S("hidden","boolean"),nt,rt})local
gt=n({_aliases={},_public_aliases={},_mincount=0,_overwrite=true},{args=6,U,S("description","string"),lt,S("convert","function","table"),X("args"),X("count"),W,S("target","string"),S("defmode","string"),S("show_default","boolean"),S("overwrite","boolean"),S("argname","string","table"),S("choices","table"),S("hidden","boolean"),nt,rt},bt)function
pt:_inherit_property(kt,qt)local jt=self while true do local xt=jt["_"..kt]if
xt~=nil then return xt end if not jt._parent then return qt end jt=jt._parent
end end function bt:_get_argument_list()local zt={}local Et=1 while
Et<=math.min(self._minargs,3)do local Tt=self:_get_argname(Et)if self._default
and self._defmode:find"a"then Tt="["..Tt.."]"end table.insert(zt,Tt)Et=Et+1 end
while Et<=math.min(self._maxargs,3)do
table.insert(zt,"["..self:_get_argname(Et).."]")Et=Et+1 if
self._maxargs==math.huge then break end end if Et<self._maxargs then
table.insert(zt,"...")end return zt end function bt:_get_usage()local
At=table.concat(self:_get_argument_list()," ")if self._default and
self._defmode:find"u"then if self._maxargs>1 or(self._minargs==1 and not
self._defmode:find"a")then At="["..At.."]"end end return At end function
it.store_true(Ot,It)Ot[It]=true end function it.store_false(Nt,St)Nt[St]=false
end function it.store(Ht,Rt,Dt)Ht[Rt]=Dt end function it.count(Lt,Ut,Ct,Mt)if
not Mt then Lt[Ut]=Lt[Ut]+1 end end function
it.append(Ft,Wt,Yt,Pt)Ft[Wt]=Ft[Wt]or{}table.insert(Ft[Wt],Yt)if Pt then
table.remove(Ft[Wt],1)end end function it.concat(Vt,Bt,Gt,Kt)if Kt then
error("'concat' action can't handle too many invocations")end
Vt[Bt]=Vt[Bt]or{}for Qt,Jt in ipairs(Gt)do table.insert(Vt[Bt],Jt)end end
function bt:_get_action()local Xt,Zt if self._maxcount==1 then if
self._maxargs==0 then Xt,Zt="store_true",nil else Xt,Zt="store",nil end else if
self._maxargs==0 then Xt,Zt="count",0 else Xt,Zt="append",{}end end if
self._action then Xt=self._action end if self._has_init then Zt=self._init end
if type(Xt)=="string"then Xt=it[Xt]end return Xt,Zt end function
bt:_get_argname(ea)local ta=self._argname or self:_get_default_argname()if
type(ta)=="table"then return ta[ea]else return ta end end function
bt:_get_choices_list()return"{"..table.concat(self._choices,",").."}"end
function bt:_get_default_argname()if self._choices then return
self:_get_choices_list()else return"<"..self._name..">"end end function
gt:_get_default_argname()if self._choices then return
self:_get_choices_list()else return"<"..self:_get_default_target()..">"end end
function bt:_get_label_lines()if self._choices then
return{self:_get_choices_list()}else return{self._name}end end function
gt:_get_label_lines()local aa=self:_get_argument_list()if#aa==0 then
return{table.concat(self._public_aliases,", ")}end local oa=-1 for ia,na in
ipairs(self._public_aliases)do oa=math.max(oa,#na)end local
sa=table.concat(aa," ")local ha={}for ra,da in ipairs(self._public_aliases)do
local la=(" "):rep(oa-#da)..da.." "..sa if ra~=#self._public_aliases then
la=la..","end table.insert(ha,la)end return ha end function
vt:_get_label_lines()return{table.concat(self._public_aliases,", ")}end
function bt:_get_description()if self._default and self._show_default then if
self._description then
return("%s (default: %s)"):format(self._description,self._default)else
return("default: %s"):format(self._default)end else return self._description
or""end end function vt:_get_description()return self._summary or
self._description or""end function gt:_get_usage()local
ua=self:_get_argument_list()table.insert(ua,1,self._name)ua=table.concat(ua," ")if
self._mincount==0 or self._default then ua="["..ua.."]"end return ua end
function bt:_get_default_target()return self._name end function
gt:_get_default_target()local ca for ma,fa in ipairs(self._public_aliases)do if
fa:sub(1,1)==fa:sub(2,2)then ca=fa:sub(3)break end end ca=ca or
self._name:sub(2)return(ca:gsub("-","_"))end function gt:_is_vararg()return
self._maxargs~=self._minargs end function pt:_get_fullname(wa)local
ya=self._parent if wa and not ya then return""end local pa={self._name}while ya
do if not wa or ya._parent then table.insert(pa,1,ya._name)end ya=ya._parent
end return table.concat(pa," ")end function pt:_update_charset(va)va=va or{}for
ba,ga in ipairs(self._commands)do ga:_update_charset(va)end for ka,qa in
ipairs(self._options)do for ka,ja in ipairs(qa._aliases)do va[ja:sub(1,1)]=true
end end return va end function pt:argument(...)local
xa=bt(...)table.insert(self._arguments,xa)return xa end function
pt:option(...)local za=gt(...)table.insert(self._options,za)return za end
function pt:flag(...)return self:option():args(0)(...)end function
pt:command(...)local Ea=vt():add_help(true)(...)Ea._parent=self
table.insert(self._commands,Ea)return Ea end function pt:mutex(...)local
Ta={...}for Aa,Oa in ipairs(Ta)do local Ia=getmetatable(Oa)assert(Ia==gt or
Ia==bt,("bad argument #%d to 'mutex' (Option or Argument expected)"):format(Aa))end
table.insert(self._mutexes,Ta)return self end function
pt:group(Na,...)assert(type(Na)=="string",("bad argument #1 to 'group' (string expected, got %s)"):format(type(Na)))local
Sa={name=Na,...}for Ha,Ra in ipairs(Sa)do local
Da=getmetatable(Ra)assert(Da==gt or Da==bt or
Da==vt,("bad argument #%d to 'group' (Option or Argument or Command expected)"):format(Ha+1))end
table.insert(self._groups,Sa)return self end local La="Usage: "function
pt:get_usage()if self._usage then return self._usage end local
Ua=self:_inherit_property("usage_margin",#La)local
Ca=self:_inherit_property("usage_max_width",70)local
Ma={La..self:_get_fullname()}local function Fa(Wa)if#Ma[#Ma]+1+#Wa<=Ca then
Ma[#Ma]=Ma[#Ma].." "..Wa else Ma[#Ma+1]=(" "):rep(Ua)..Wa end end local
Ya={}local Pa={}local Va={}local Ba={}local function Ga(Ka,Qa)if Va[Ka]then
return end Va[Ka]=true local Ja={}for Xa,Za in ipairs(Ka)do if not Za._hidden
and not Pa[Za]then if getmetatable(Za)==gt or Za==Qa then
table.insert(Ja,Za:_get_usage())Pa[Za]=true end end end if#Ja==1 then
Fa(Ja[1])elseif#Ja>1 then Fa("("..table.concat(Ja," | ")..")")end end local
function eo(to)if not to._hidden and not Pa[to]then
Fa(to:_get_usage())Pa[to]=true end end for ao,oo in ipairs(self._mutexes)do
local no=false local so=false for ao,ho in ipairs(oo)do if getmetatable(ho)==gt
then if ho:_is_vararg()then no=true end else so=true
Ba[ho]=Ba[ho]or{}table.insert(Ba[ho],oo)end Ya[ho]=true end if not no and not
so then Ga(oo)end end for ro,lo in ipairs(self._options)do if not Ya[lo]and not
lo:_is_vararg()then eo(lo)end end for uo,co in ipairs(self._arguments)do local
mo if Ya[co]then for uo,fo in ipairs(Ba[co])do if not Va[fo]then mo=fo end end
end if mo then Ga(mo,co)else eo(co)end end for wo,yo in ipairs(self._mutexes)do
Ga(yo)end for po,vo in ipairs(self._options)do eo(vo)end if#self._commands>0
then if self._require_command then Fa("<command>")else Fa("[<command>]")end
Fa("...")end return table.concat(Ma,"\n")end local function bo(go)if go==""then
return{}end local ko={}if go:sub(-1)~="\n"then go=go.."\n"end for qo in
go:gmatch("([^\n]*)\n")do table.insert(ko,qo)end return ko end local function
jo(xo,zo)local Eo={}local To=xo:match("^ *")if xo:find("^ *[%*%+%-]")then
To=To.." "..xo:match("^ *[%*%+%-]( *)")end local Ao={}local Oo=0 local Io=1
while true do local No,So,Ho=xo:find("([^ ]+)",Io)if not No then break end
local Ro=xo:sub(Io,No-1)Io=So+1 if(#Ao==0)or(Oo+#Ro+#Ho<=zo)then
table.insert(Ao,Ro)table.insert(Ao,Ho)Oo=Oo+#Ro+#Ho else
table.insert(Eo,table.concat(Ao))Ao={To,Ho}Oo=#To+#Ho end end if#Ao>0 then
table.insert(Eo,table.concat(Ao))end if#Eo==0 then Eo[1]=""end return Eo end
local function Do(Lo,Uo)local Co={}for Mo,Fo in ipairs(Lo)do local
Wo=jo(Fo,Uo)for Mo,Yo in ipairs(Wo)do table.insert(Co,Yo)end end return Co end
function pt:_get_element_help(Po)local Vo=Po:_get_label_lines()local
Bo=bo(Po:_get_description())local Go={}local
Ko=self:_inherit_property("help_usage_margin",1)local Qo=(" "):rep(Ko)local
Jo=self:_inherit_property("help_description_margin",23)local
Xo=(" "):rep(Jo)local Zo=self:_inherit_property("help_max_width")if Zo then
local ei=math.max(Zo-Jo,10)Bo=Do(Bo,ei)end if#Vo[1]>=(Jo-Ko)then for ti,ai in
ipairs(Vo)do table.insert(Go,Qo..ai)end for oi,ii in ipairs(Bo)do
table.insert(Go,Xo..ii)end else for ni=1,math.max(#Vo,#Bo)do local
si=Vo[ni]local hi=Bo[ni]local ri=""if si then ri=Qo..si end if hi and
hi~=""then ri=ri..(" "):rep(Jo-#ri)..hi end table.insert(Go,ri)end end return
table.concat(Go,"\n")end local function di(li)local ui={}for ci,mi in
ipairs(li)do ui[getmetatable(mi)]=true end return ui end function
pt:_add_group_help(fi,wi,yi,pi)local vi={yi}for bi,gi in ipairs(pi)do if not
gi._hidden and not wi[gi]then wi[gi]=true
table.insert(vi,self:_get_element_help(gi))end end if#vi>1 then
table.insert(fi,table.concat(vi,("\n"):rep(self:_inherit_property("help_vertical_space",0)+1)))end
end function pt:get_help()if self._help then return self._help end local
ki={self:get_usage()}local qi=self:_inherit_property("help_max_width")if
self._description then local ji=self._description if qi then
ji=table.concat(Do(bo(ji),qi),"\n")end table.insert(ki,ji)end local
xi={[bt]={},[gt]={},[vt]={}}for zi,Ei in ipairs(self._groups)do local
Ti=di(Ei)for zi,Ai in ipairs({bt,gt,vt})do if Ti[Ai]then
table.insert(xi[Ai],Ei)break end end end local
Oi={{name="Arguments",type=bt,elements=self._arguments},{name="Options",type=gt,elements=self._options},{name="Commands",type=vt,elements=self._commands}}local
Ii={}for Ni,Si in ipairs(Oi)do local Hi=xi[Si.type]for Ni,Ri in ipairs(Hi)do
self:_add_group_help(ki,Ii,Ri.name..":",Ri)end local Di=Si.name..":"if#Hi>0
then Di="Other "..Di:gsub("^.",string.lower)end
self:_add_group_help(ki,Ii,Di,Si.elements)end if self._epilog then local
Li=self._epilog if qi then Li=table.concat(Do(bo(Li),qi),"\n")end
table.insert(ki,Li)end return table.concat(ki,"\n\n")end function
pt:add_help_command(Ui)if Ui then assert(type(Ui)=="string"or
type(Ui)=="table",("bad argument #1 to 'add_help_command' (string or table expected, got %s)"):format(type(Ui)))end
local
Ci=self:command():description"Show help for commands."Ci:argument"command":description"The command to show help for.":args"?":action(function(Mi,Mi,Fi)if
not Fi then print(self:get_help())error()else for Mi,Wi in
ipairs(self._commands)do for Mi,Yi in ipairs(Wi._aliases)do if Yi==Fi then
print(Wi:get_help())error()end end end end
Ci:error(("unknown command '%s'"):format(Fi))end)if Ui then Ci=Ci(Ui)end if not
Ci._name then Ci"help"end Ci._is_help_command=true return self end function
pt:_is_shell_safe()if self._basename then if
self._basename:find("[^%w_%-%+%.]")then return false end else for Pi,Vi in
ipairs(self._aliases)do if Vi:find("[^%w_%-%+%.]")then return false end end end
for Bi,Gi in ipairs(self._options)do for Bi,Ki in ipairs(Gi._aliases)do if
Ki:find("[^%w_%-%+%.]")then return false end end if Gi._choices then for Bi,Qi
in ipairs(Gi._choices)do if Qi:find("[%s'\"]")then return false end end end end
for Ji,Xi in ipairs(self._arguments)do if Xi._choices then for Ji,Zi in
ipairs(Xi._choices)do if Zi:find("[%s'\"]")then return false end end end end
for en,tn in ipairs(self._commands)do if not tn:_is_shell_safe()then return
false end end return true end function pt:add_complete(an)if an then
assert(type(an)=="string"or
type(an)=="table",("bad argument #1 to 'add_complete' (string or table expected, got %s)"):format(type(an)))end
local
on=self:option():description"Output a shell completion script for the specified shell.":args(1):choices{"bash","zsh","fish"}:action(function(nn,nn,sn)io.write(self["get_"..sn.."_complete"](self))error()end)if
an then on=on(an)end if not on._name then on"--completion"end return self end
function pt:add_complete_command(hn)if hn then assert(type(hn)=="string"or
type(hn)=="table",("bad argument #1 to 'add_complete_command' (string or table expected, got %s)"):format(type(hn)))end
local
rn=self:command():description"Output a shell completion script."rn:argument"shell":description"The shell to output a completion script for.":choices{"bash","zsh","fish"}:action(function(dn,dn,ln)io.write(self["get_"..ln.."_complete"](self))error()end)if
hn then rn=rn(hn)end if not rn._name then rn"completion"end return self end
local function un(cn)return cn:gsub("[/\\]*$",""):match(".*[/\\]([^/\\]*)")or
cn end local function mn(fn)local
wn=fn:_get_description():match("^(.-)%.%s")return wn or
fn:_get_description():match("^(.-)%.?$")end function pt:_get_options()local
yn={}for pn,vn in ipairs(self._options)do for pn,bn in ipairs(vn._aliases)do
table.insert(yn,bn)end end return table.concat(yn," ")end function
pt:_get_commands()local gn={}for kn,qn in ipairs(self._commands)do for kn,jn in
ipairs(qn._aliases)do table.insert(gn,jn)end end return table.concat(gn," ")end
function pt:_bash_option_args(xn,zn)local En={}for Tn,An in
ipairs(self._options)do if An._choices or An._minargs>0 then local On if
An._choices then
On='COMPREPLY=($(compgen -W "'..table.concat(An._choices," ")..'" -- "$cur"))'else
On='COMPREPLY=($(compgen -f -- "$cur"))'end
table.insert(En,(" "):rep(zn+4)..table.concat(An._aliases,"|")..")")table.insert(En,(" "):rep(zn+8)..On)table.insert(En,(" "):rep(zn+8).."return 0")table.insert(En,(" "):rep(zn+8)..";;")end
end if#En>0 then
table.insert(xn,(" "):rep(zn)..'case "$prev" in')table.insert(xn,table.concat(En,"\n"))table.insert(xn,(" "):rep(zn).."esac\n")end
end function pt:_bash_get_cmd(In,Nn)if#self._commands==0 then return end
table.insert(In,(" "):rep(Nn)..'args=("${args[@]:1}")')table.insert(In,(" "):rep(Nn)..'for arg in "${args[@]}"; do')table.insert(In,(" "):rep(Nn+4)..'case "$arg" in')for
Sn,Hn in ipairs(self._commands)do
table.insert(In,(" "):rep(Nn+8)..table.concat(Hn._aliases,"|")..")")if
self._parent then
table.insert(In,(" "):rep(Nn+12)..'cmd="$cmd '..Hn._name..'"')else
table.insert(In,(" "):rep(Nn+12)..'cmd="'..Hn._name..'"')end
table.insert(In,(" "):rep(Nn+12)..'opts="$opts '..Hn:_get_options()..'"')Hn:_bash_get_cmd(In,Nn+12)table.insert(In,(" "):rep(Nn+12).."break")table.insert(In,(" "):rep(Nn+12)..";;")end
table.insert(In,(" "):rep(Nn+4).."esac")table.insert(In,(" "):rep(Nn).."done")end
function pt:_bash_cmd_completions(Rn)local Dn={}if self._parent then
self:_bash_option_args(Dn,12)end if#self._commands>0 then
table.insert(Dn,(" "):rep(12)..'COMPREPLY=($(compgen -W "'..self:_get_commands()..'" -- "$cur"))')elseif
self._is_help_command then
table.insert(Dn,(" "):rep(12)..'COMPREPLY=($(compgen -W "'..self._parent:_get_commands()..'" -- "$cur"))')end
if#Dn>0 then
table.insert(Rn,(" "):rep(8).."'"..self:_get_fullname(true).."')")table.insert(Rn,table.concat(Dn,"\n"))table.insert(Rn,(" "):rep(12)..";;")end
for Ln,Un in ipairs(self._commands)do Un:_bash_cmd_completions(Rn)end end
function
pt:get_bash_complete()self._basename=un(self._name)assert(self:_is_shell_safe())local
Cn={([[
_%s() {
    local IFS=$' \t\n'
    local args cur prev cmd opts arg
    args=("${COMP_WORDS[@]}")
    cur="${COMP_WORDS[COMP_CWORD]}"
    prev="${COMP_WORDS[COMP_CWORD-1]}"
    opts="%s"
]]):format(self._basename,self:_get_options())}self:_bash_option_args(Cn,4)self:_bash_get_cmd(Cn,4)if#self._commands>0
then
table.insert(Cn,"")table.insert(Cn,(" "):rep(4)..'case "$cmd" in')self:_bash_cmd_completions(Cn)table.insert(Cn,(" "):rep(4).."esac\n")end
table.insert(Cn,([=[
    if [[ "$cur" = -* ]]; then
        COMPREPLY=($(compgen -W "$opts" -- "$cur"))
    fi
}

complete -F _%s -o bashdefault -o default %s
]=]):format(self._basename,self._basename))return
table.concat(Cn,"\n")end function pt:_zsh_arguments(Mn,Fn,Wn)if self._parent
then
table.insert(Mn,(" "):rep(Wn).."options=(")table.insert(Mn,(" "):rep(Wn+2).."$options")else
table.insert(Mn,(" "):rep(Wn).."local -a options=(")end for Yn,Pn in
ipairs(self._options)do local Vn={}if#Pn._aliases>1 then if Pn._maxcount>1 then
table.insert(Vn,'"*"')end
table.insert(Vn,"{"..table.concat(Pn._aliases,",")..'}"')else
table.insert(Vn,'"')if Pn._maxcount>1 then table.insert(Vn,"*")end
table.insert(Vn,Pn._name)end if Pn._description then local
Bn=mn(Pn):gsub('["%]:`$]',"\\%0")table.insert(Vn,"["..Bn.."]")end if
Pn._maxargs==math.huge then table.insert(Vn,":*")end if Pn._choices then
table.insert(Vn,": :("..table.concat(Pn._choices," ")..")")elseif Pn._maxargs>0
then table.insert(Vn,": :_files")end
table.insert(Vn,'"')table.insert(Mn,(" "):rep(Wn+2)..table.concat(Vn))end
table.insert(Mn,(" "):rep(Wn)..")")table.insert(Mn,(" "):rep(Wn).."_arguments -s -S \\")table.insert(Mn,(" "):rep(Wn+2).."$options \\")if
self._is_help_command then
table.insert(Mn,(" "):rep(Wn+2)..'": :('..self._parent:_get_commands()..')" \\')else
for Gn,Kn in ipairs(self._arguments)do local Qn if Kn._choices then
Qn=": :("..table.concat(Kn._choices," ")..")"else Qn=": :_files"end if
Kn._maxargs==math.huge then
table.insert(Mn,(" "):rep(Wn+2)..'"*'..Qn..'" \\')break end for
Gn=1,Kn._maxargs do table.insert(Mn,(" "):rep(Wn+2)..'"'..Qn..'" \\')end end
if#self._commands>0 then
table.insert(Mn,(" "):rep(Wn+2)..'": :_'..Fn..'_cmds" \\')table.insert(Mn,(" "):rep(Wn+2)..'"*:: :->args" \\')end
end table.insert(Mn,(" "):rep(Wn+2).."&& return 0")end function
pt:_zsh_cmds(Jn,Xn)table.insert(Jn,"\n_"..Xn.."_cmds() {")table.insert(Jn,"  local -a commands=(")for
Zn,es in ipairs(self._commands)do local ts={}if#es._aliases>1 then
table.insert(ts,"{"..table.concat(es._aliases,",")..'}"')else
table.insert(ts,'"'..es._name)end if es._description then
table.insert(ts,":"..mn(es):gsub('["`$]',"\\%0"))end
table.insert(Jn,"    "..table.concat(ts)..'"')end
table.insert(Jn,'  )\n  _describe "command" commands\n}')end function
pt:_zsh_complete_help(as,os,is,ns)if#self._commands==0 then return end
self:_zsh_cmds(os,is)table.insert(as,"\n"..(" "):rep(ns).."case $words[1] in")for
ss,hs in ipairs(self._commands)do local rs=is.."_"..hs._name
table.insert(as,(" "):rep(ns+2)..table.concat(hs._aliases,"|")..")")hs:_zsh_arguments(as,rs,ns+4)hs:_zsh_complete_help(as,os,rs,ns+4)table.insert(as,(" "):rep(ns+4)..";;\n")end
table.insert(as,(" "):rep(ns).."esac")end function
pt:get_zsh_complete()self._basename=un(self._name)assert(self:_is_shell_safe())local
ds={("#compdef %s\n"):format(self._basename)}local
ls={}table.insert(ds,"_"..self._basename.."() {")if#self._commands>0 then
table.insert(ds,"  local context state state_descr line")table.insert(ds,"  typeset -A opt_args\n")end
self:_zsh_arguments(ds,self._basename,2)self:_zsh_complete_help(ds,ls,self._basename,2)table.insert(ds,"\n  return 1")table.insert(ds,"}")local
us=table.concat(ds,"\n")if#ls>0 then us=us.."\n"..table.concat(ls,"\n")end
return us.."\n\n_"..self._basename.."\n"end local function cs(ms)return
ms:gsub("[\\']","\\%0")end function pt:_fish_get_cmd(fs,ws)if#self._commands==0
then return end
table.insert(fs,(" "):rep(ws).."set -e cmdline[1]")table.insert(fs,(" "):rep(ws).."for arg in $cmdline")table.insert(fs,(" "):rep(ws+4).."switch $arg")for
ys,ps in ipairs(self._commands)do
table.insert(fs,(" "):rep(ws+8).."case "..table.concat(ps._aliases," "))table.insert(fs,(" "):rep(ws+12).."set cmd $cmd "..ps._name)ps:_fish_get_cmd(fs,ws+12)table.insert(fs,(" "):rep(ws+12).."break")end
table.insert(fs,(" "):rep(ws+4).."end")table.insert(fs,(" "):rep(ws).."end")end
function pt:_fish_complete_help(vs,bs)local gs="complete -c "..bs
table.insert(vs,"")for ks,qs in ipairs(self._commands)do local
js=table.concat(qs._aliases," ")local xs if self._parent then
xs=("%s -n '__fish_%s_using_command %s' -xa '%s'"):format(gs,bs,self:_get_fullname(true),js)else
xs=("%s -n '__fish_%s_using_command' -xa '%s'"):format(gs,bs,js)end if
qs._description then xs=("%s -d '%s'"):format(xs,cs(mn(qs)))end
table.insert(vs,xs)end if self._is_help_command then local
zs=("%s -n '__fish_%s_using_command %s' -xa '%s'"):format(gs,bs,self:_get_fullname(true),self._parent:_get_commands())table.insert(vs,zs)end
for Es,Ts in ipairs(self._options)do local As={gs}if self._parent then
table.insert(As,"-n '__fish_"..bs.."_seen_command "..self:_get_fullname(true).."'")end
for Es,Os in ipairs(Ts._aliases)do if Os:match("^%-.$")then
table.insert(As,"-s "..Os:sub(2))elseif Os:match("^%-%-.+")then
table.insert(As,"-l "..Os:sub(3))end end if Ts._choices then
table.insert(As,"-xa '"..table.concat(Ts._choices," ").."'")elseif
Ts._minargs>0 then table.insert(As,"-r")end if Ts._description then
table.insert(As,"-d '"..cs(mn(Ts)).."'")end
table.insert(vs,table.concat(As," "))end for Is,Ns in ipairs(self._commands)do
Ns:_fish_complete_help(vs,bs)end end function
pt:get_fish_complete()self._basename=un(self._name)assert(self:_is_shell_safe())local
Ss={}if#self._commands>0 then
table.insert(Ss,([[
function __fish_%s_print_command
    set -l cmdline (commandline -poc)
    set -l cmd]]):format(self._basename))self:_fish_get_cmd(Ss,4)table.insert(Ss,([[
    echo "$cmd"
end

function __fish_%s_using_command
    test (__fish_%s_print_command) = "$argv"
    and return 0
    or return 1
end

function __fish_%s_seen_command
    string match -q "$argv*" (__fish_%s_print_command)
    and return 0
    or return 1
end]]):format(self._basename,self._basename,self._basename,self._basename))end
self:_fish_complete_help(Ss,self._basename)return
table.concat(Ss,"\n").."\n"end local function Hs(Rs,Ds)local Ls={}local Us
local Cs={}for Ms in pairs(Rs)do if type(Ms)=="string"then for Fs=1,#Ms do
Us=Ms:sub(1,Fs-1)..Ms:sub(Fs+1)if not Ls[Us]then Ls[Us]={}end
table.insert(Ls[Us],Ms)end end end for Ws=1,#Ds+1 do
Us=Ds:sub(1,Ws-1)..Ds:sub(Ws+1)if Rs[Us]then Cs[Us]=true elseif Ls[Us]then for
Ys,Ps in ipairs(Ls[Us])do Cs[Ps]=true end end end local Vs=next(Cs)if Vs then
if next(Cs,Vs)then local Bs={}for Gs in pairs(Cs)do
table.insert(Bs,"'"..Gs.."'")end
table.sort(Bs)return"\nDid you mean one of these: "..table.concat(Bs," ").."?"else
return"\nDid you mean '"..Vs.."'?"end else return""end end local
Ks=n({invocations=0})function Ks:__call(Qs,Js)self.state=Qs
self.result=Qs.result self.element=Js self.target=Js._target or
Js:_get_default_target()self.action,self.result[self.target]=Js:_get_action()return
self end function Ks:error(Xs,...)self.state:error(Xs,...)end function
Ks:convert(Zs,eh)local th=self.element._convert if th then local ah,oh if
type(th)=="function"then ah,oh=th(Zs)elseif type(th[eh])=="function"then
ah,oh=th[eh](Zs)else ah=th[Zs]end if ah==nil then self:error(oh
and"%s"or"malformed argument '%s'",oh or Zs)end Zs=ah end return Zs end
function Ks:default(ih)return self.element._defmode:find(ih)and
self.element._default end local function nh(sh,hh,rh,dh)local lh=""if hh~=rh
then lh="at "..(dh and"most"or"least").." "end local uh=dh and rh or hh return
lh..tostring(uh).." "..sh..(uh==1 and""or"s")end function
Ks:set_name(ch)self.name=("%s '%s'"):format(ch and"option"or"argument",ch or
self.element._name)end function Ks:invoke()self.open=true self.overwrite=false
if self.invocations>=self.element._maxcount then if self.element._overwrite
then self.overwrite=true else local
mh=nh("time",self.element._mincount,self.element._maxcount,true)self:error("%s must be used %s",self.name,mh)end
else self.invocations=self.invocations+1 end self.args={}if
self.element._maxargs<=0 then self:close()end return self.open end function
Ks:check_choices(fh)if self.element._choices then for wh,yh in
ipairs(self.element._choices)do if fh==yh then return end end local
ph="'"..table.concat(self.element._choices,"', '").."'"local
vh=getmetatable(self.element)==gt self:error("%s%s must be one of %s",vh
and"argument for "or"",self.name,ph)end end function
Ks:pass(bh)self:check_choices(bh)bh=self:convert(bh,#self.args+1)table.insert(self.args,bh)if#self.args>=self.element._maxargs
then self:close()end return self.open end function
Ks:complete_invocation()while#self.args<self.element._minargs do
self:pass(self.element._default)end end function Ks:close()if self.open then
self.open=false if#self.args<self.element._minargs then if
self:default("a")then self:complete_invocation()else if#self.args==0 then if
getmetatable(self.element)==bt then self:error("missing %s",self.name)elseif
self.element._maxargs==1 then
self:error("%s requires an argument",self.name)end end
self:error("%s requires %s",self.name,nh("argument",self.element._minargs,self.element._maxargs))end
end local gh if self.element._maxargs==0 then gh=self.args[1]elseif
self.element._maxargs==1 then if self.element._minargs==0 and
self.element._mincount~=self.element._maxcount then gh=self.args else
gh=self.args[1]end else gh=self.args end
self.action(self.result,self.target,gh,self.overwrite)end end local
kh=n({result={},options={},arguments={},argument_i=1,element_to_mutexes={},mutex_to_element_state={},command_actions={}})function
kh:__call(qh,jh)self.parser=qh self.error_handler=jh
self.charset=qh:_update_charset()self:switch(qh)return self end function
kh:error(xh,...)self.error_handler(self.parser,xh:format(...))end function
kh:switch(zh)self.parser=zh if zh._action then
table.insert(self.command_actions,{action=zh._action,name=zh._name})end for
Eh,Th in ipairs(zh._options)do Th=Ks(self,Th)table.insert(self.options,Th)for
Eh,Ah in ipairs(Th.element._aliases)do self.options[Ah]=Th end end for Oh,Ih in
ipairs(zh._mutexes)do for Oh,Nh in ipairs(Ih)do if not
self.element_to_mutexes[Nh]then self.element_to_mutexes[Nh]={}end
table.insert(self.element_to_mutexes[Nh],Ih)end end for Sh,Hh in
ipairs(zh._arguments)do
Hh=Ks(self,Hh)table.insert(self.arguments,Hh)Hh:set_name()Hh:invoke()end
self.handle_options=zh._handle_options
self.argument=self.arguments[self.argument_i]self.commands=zh._commands for
Rh,Dh in ipairs(self.commands)do for Rh,Lh in ipairs(Dh._aliases)do
self.commands[Lh]=Dh end end end function kh:get_option(Uh)local
Ch=self.options[Uh]if not Ch then
self:error("unknown option '%s'%s",Uh,Hs(self.options,Uh))else return Ch end
end function kh:get_command(Mh)local Fh=self.commands[Mh]if not Fh then
if#self.commands>0 then
self:error("unknown command '%s'%s",Mh,Hs(self.commands,Mh))else
self:error("too many arguments")end else return Fh end end function
kh:check_mutexes(Wh)if self.element_to_mutexes[Wh.element]then for Yh,Ph in
ipairs(self.element_to_mutexes[Wh.element])do local
Vh=self.mutex_to_element_state[Ph]if Vh and Vh~=Wh then
self:error("%s can not be used together with %s",Wh.name,Vh.name)else
self.mutex_to_element_state[Ph]=Wh end end end end function
kh:invoke(Bh,Gh)self:close()Bh:set_name(Gh)self:check_mutexes(Bh,Gh)if
Bh:invoke()then self.option=Bh end end function kh:pass(Kh)if self.option then
if not self.option:pass(Kh)then self.option=nil end elseif self.argument then
self:check_mutexes(self.argument)if not self.argument:pass(Kh)then
self.argument_i=self.argument_i+1
self.argument=self.arguments[self.argument_i]end else local
Qh=self:get_command(Kh)self.result[Qh._target or Qh._name]=true if
self.parser._command_target then
self.result[self.parser._command_target]=Qh._name end self:switch(Qh)end end
function kh:close()if self.option then self.option:close()self.option=nil end
end function kh:finalize()self:close()for Jh=self.argument_i,#self.arguments do
local Xh=self.arguments[Jh]if#Xh.args==0 and Xh:default("u")then
Xh:complete_invocation()else Xh:close()end end if self.parser._require_command
and#self.commands>0 then self:error("a command is required")end for Zh,er in
ipairs(self.options)do er.name=er.name
or("option '%s'"):format(er.element._name)if er.invocations==0 then if
er:default("u")then er:invoke()er:complete_invocation()er:close()end end local
tr=er.element._mincount if er.invocations<tr then if er:default("a")then while
er.invocations<tr do er:invoke()er:close()end elseif er.invocations==0 then
self:error("missing %s",er.name)else
self:error("%s must be used %s",er.name,nh("time",tr,er.element._maxcount))end
end end for ar=#self.command_actions,1,-1 do
self.command_actions[ar].action(self.result,self.command_actions[ar].name)end
end function kh:parse(ir)for nr,sr in ipairs(ir)do local hr=true if
self.handle_options then local rr=sr:sub(1,1)if self.charset[rr]then if#sr>1
then hr=false if sr:sub(2,2)==rr then if#sr==2 then if self.options[sr]then
local dr=self:get_option(sr)self:invoke(dr,sr)else self:close()end
self.handle_options=false else local lr=sr:find"="if lr then local
ur=sr:sub(1,lr-1)local cr=self:get_option(ur)if cr.element._maxargs<=0 then
self:error("option '%s' does not take arguments",ur)end
self:invoke(cr,ur)self:pass(sr:sub(lr+1))else local
mr=self:get_option(sr)self:invoke(mr,sr)end end else for fr=2,#sr do local
wr=rr..sr:sub(fr,fr)local yr=self:get_option(wr)self:invoke(yr,wr)if fr~=#sr
and yr.element._maxargs>0 then self:pass(sr:sub(fr+1))break end end end end end
end if hr then self:pass(sr)end end self:finalize()return self.result end
function
pt:error(pr)io.stderr:write(("%s\n\nError: %s\n"):format(self:get_usage(),pr))error()end
local vr=rawget(_G,"arg")or{}function pt:_parse(br,gr)return
kh(self,gr):parse(br or vr)end function pt:parse(kr)return
self:_parse(kr,self.error)end local function qr(jr)return
tostring(jr).."\noriginal "..debug.traceback("",2):sub(2)end function
pt:pparse(xr)local zr local Er,Tr=xpcall(function()return
self:_parse(xr,function(Ar,Or)zr=Or error(Or,0)end)end,qr)if Er then return
true,Tr elseif not zr then error(Tr,0)else return false,zr end end local
Ir={}Ir.version="0.7.3"setmetatable(Ir,{__call=function(Nr,...)return
pt(vr[0]):add_help(true)(...)end})return
Ir
]==],
['youcube/lib/string_pack.lua'] = [=[
-- MIT License
--
-- Copyright (c) 2021 JackMacWindows
--
-- Permission is hereby granted, free of charge, to any person obtaining a copy
-- of this software and associated documentation files (the "Software"), to deal
-- in the Software without restriction, including without limitation the rights
-- to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
-- copies of the Software, and to permit persons to whom the Software is
-- furnished to do so, subject to the following conditions:
--
-- The above copyright notice and this permission notice shall be included in all
-- copies or substantial portions of the Software.
--
-- THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
-- IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
-- FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
-- AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
-- LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
-- OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
-- SOFTWARE.

-- If not using CC, replace `expect` with a suitable argument checking function.
--local expect = require "cc.expect".expect
local expect = dofile "/rom/modules/main/cc/expect.lua".expect

local ByteOrder = {BIG_ENDIAN = 1, LITTLE_ENDIAN = 2}
local isint = {b = 1, B = 1, h = 1, H = 1, l = 1, L = 1, j = 1, J = 1, T = 1}
local packoptsize_tbl = {b = 1, B = 1, x = 1, h = 2, H = 2, f = 4, j = 4, J = 4, l = 8, L = 8, T = 8, d = 8, n = 8}

local function round(n) if n % 1 >= 0.5 then return math.ceil(n) else return math.floor(n) end end

local function floatToRawIntBits(f)
    if f == 0 then return 0
    elseif f == -0 then return 0x80000000
    elseif f == math.huge then return 0x7F800000
    elseif f == -math.huge then return 0xFF800000 end
    local m, e = math.frexp(f)
    if e > 127 or e < -126 then error("number out of range", 3) end
    e, m = e + 126, round((math.abs(m) - 0.5) * 0x1000000)
    if m > 0x7FFFFF then e = e + 1 end
    return bit32.bor(f < 0 and 0x80000000 or 0, bit32.lshift(bit32.band(e, 0xFF), 23), bit32.band(m, 0x7FFFFF))
end

local function doubleToRawLongBits(f)
    if f == 0 then return 0, 0
    elseif f == -0 then return 0x80000000, 0
    elseif f == math.huge then return 0x7FF00000, 0
    elseif f == -math.huge then return 0xFFF00000, 0 end
    local m, e = math.frexp(f)
    if e > 1023 or e < -1022 then error("number out of range", 3) end
    e, m = e + 1022, round((math.abs(m) - 0.5) * 0x20000000000000)
    if m > 0xFFFFFFFFFFFFF then e = e + 1 end
    return bit32.bor(f < 0 and 0x80000000 or 0, bit32.lshift(bit32.band(e, 0x7FF), 20), bit32.band(m / 0x100000000, 0xFFFFF)), bit32.band(m, 0xFFFFFFFF)
end

local function intBitsToFloat(l)
    if l == 0 then return 0
    elseif l == 0x80000000 then return -0
    elseif l == 0x7F800000 then return math.huge
    elseif l == 0xFF800000 then return -math.huge end
    local m, e = bit32.band(l, 0x7FFFFF), bit32.band(bit32.rshift(l, 23), 0xFF)
    e, m = e - 126, m / 0x1000000 + 0.5
    local n = math.ldexp(m, e)
    return bit32.btest(l, 0x80000000) and -n or n
end

local function longBitsToDouble(lh, ll)
    if lh == 0 and ll == 0 then return 0
    elseif lh == 0x80000000 and ll == 0 then return -0
    elseif lh == 0x7FF00000 and ll == 0 then return math.huge
    elseif lh == 0xFFF00000 and ll == 0 then return -math.huge end
    local m, e = bit32.band(lh, 0xFFFFF) * 0x100000000 + bit32.band(ll, 0xFFFFFFFF), bit32.band(bit32.rshift(lh, 20), 0x7FF)
    e, m = e - 1022, m / 0x20000000000000 + 0.5
    local n = math.ldexp(m, e)
    return bit32.btest(lh, 0x80000000) and -n or n
end

local function packint(num, size, output, offset, alignment, endianness, signed)
    local total_size = 0
    if offset % math.min(size, alignment) ~= 0 and alignment > 1 then
        local i = 0
        while offset % math.min(size, alignment) ~= 0 and i < alignment do
            output[offset] = 0
            offset = offset + 1
            total_size = total_size + 1
            i = i + 1
        end
    end
    if endianness == ByteOrder.BIG_ENDIAN then
        local added_padding = 0
        if size > 8 then for i = 0, size - 9 do
            output[offset + i] = (signed and num >= 2^(size * 8 - 1) ~= 0) and 0xFF or 0
            added_padding = added_padding + 1
            total_size = total_size + 1
        end end
        for i = added_padding, size - 1 do
            output[offset + i] = bit32.band(bit32.rshift(num, ((size - i - 1) * 8)), 0xFF)
            total_size = total_size + 1
        end
    else
        for i = 0, math.min(size, 8) - 1 do
            output[offset + i] = num / 2^(i * 8) % 256
            total_size = total_size + 1
        end
        for i = 8, size - 1 do
            output[offset + i] = (signed and num >= 2^(size * 8 - 1) ~= 0) and 0xFF or 0
            total_size = total_size + 1
        end
    end
    return total_size
end

local function unpackint(str, offset, size, endianness, alignment, signed)
    local result, rsize = 0, 0
    if offset % math.min(size, alignment) ~= 0 and alignment > 1 then
        for i = 0, alignment - 1 do
            if offset % math.min(size, alignment) == 0 then break end
            offset = offset + 1
            rsize = rsize + 1
        end
    end
    for i = 0, size - 1 do
        result = result + str:byte(offset + i) * 2^((endianness == ByteOrder.BIG_ENDIAN and size - i - 1 or i) * 8)
        rsize = rsize + 1
    end
    if (signed and result >= 2^(size * 8 - 1)) then result = result - 2^(size * 8) end
    return result, rsize
end

local function packoptsize(opt, alignment)
    local retval = packoptsize_tbl[opt] or 0
    if (alignment > 1 and retval % alignment ~= 0) then retval = retval + (alignment - (retval % alignment)) end
    return retval
end

--[[
 * string.pack (fmt, v1, v2, ...)
 *
 * Returns a binary string containing the values v1, v2, etc.
 * serialized in binary form (packed) according to the format string fmt.
 ]]
local function pack(...)
    local fmt = expect(1, ..., "string")
    local endianness = ByteOrder.LITTLE_ENDIAN
    local alignment = 1
    local pos = 1
    local argnum = 2
    local output = {}
    local i = 1
    while i <= #fmt do
        local c = fmt:sub(i, i)
        i = i + 1
        if c == '=' or c == '<' then
            endianness = ByteOrder.LITTLE_ENDIAN
        elseif c == '>' then
            endianness = ByteOrder.BIG_ENDIAN
        elseif c == '!' then
            local size = -1
            while (i <= #fmt and fmt:sub(i, i):match("%d")) do
                if (size >= 0xFFFFFFFF / 10) then error("bad argument #1 to 'pack' (invalid format)", 2) end
                size = (math.max(size, 0) * 10) + tonumber(fmt:sub(i, i))
                i = i + 1
            end
            if (size > 16 or size == 0) then error(string.format("integral size (%d) out of limits [1,16]", size), 2)
            elseif (size == -1) then alignment = 4
            else alignment = size end
        elseif isint[c] then
            local num = expect(argnum, select(argnum, ...), "number")
            argnum = argnum + 1
            if (num >= math.pow(2, (packoptsize(c, 0) * 8 - (c:match("%l") and 1 or 0))) or
                num < (c:match("%l") and -math.pow(2, (packoptsize(c, 0) * 8 - 1)) or 0)) then
                error(string.format("bad argument #%d to 'pack' (integer overflow)", argnum - 1), 2)
            end
            pos = pos + packint(num, packoptsize(c, 0), output, pos, alignment, endianness, false)
        elseif c:lower() == 'i' then
            local signed = c == 'i'
            local size = -1
            while i <= #fmt and fmt:sub(i, i):match("%d") do
                if (size >= 0xFFFFFFFF / 10) then error("bad argument #1 to 'pack' (invalid format)", 2) end
                size = (math.max(size, 0) * 10) + tonumber(fmt:sub(i, i))
                i = i + 1
            end
            if (size > 16 or size == 0) then
                error(string.format("integral size (%d) out of limits [1,16]", size), 2)
            elseif (alignment > 1 and (size ~= 1 and size ~= 2 and size ~= 4 and size ~= 8 and size ~= 16)) then
                error("bad argument #1 to 'pack' (format asks for alignment not power of 2)", 2)
            elseif (size == -1) then size = 4 end
            local num = expect(argnum, select(argnum, ...), "number")
            argnum = argnum + 1
            if (num >= math.pow(2, (size * 8 - (c:match("%l") and 1 or 0))) or
                num < (c:match("%l") and -math.pow(2, (size * 8 - 1)) or 0)) then
                error(string.format("bad argument #%d to 'pack' (integer overflow)", argnum - 1), 2)
            end
            pos = pos + packint(num, size, output, pos, alignment, endianness, signed)
        elseif c == 'f' then
            local f = expect(argnum, select(argnum, ...), "number")
            argnum = argnum + 1
            local l = floatToRawIntBits(f)
            if (pos % math.min(4, alignment) ~= 0 and alignment > 1) then 
                for j = 0, alignment - 1 do
                    if pos % math.min(4, alignment) == 0 then break end
                    output[pos] = 0
                    pos = pos + 1
                end
            end
            for j = 0, 3 do output[pos + (endianness == ByteOrder.BIG_ENDIAN and 3 - j or j)] = bit32.band(bit32.rshift(l, (j * 8)), 0xFF) end
            pos = pos + 4
        elseif c == 'd' or c == 'n' then
            local f = expect(argnum, select(argnum, ...), "number")
            argnum = argnum + 1
            local lh, ll = doubleToRawLongBits(f)
            if (pos % math.min(8, alignment) ~= 0 and alignment > 1) then 
                for j = 0, alignment - 1 do
                    if pos % math.min(8, alignment) == 0 then break end
                    output[pos] = 0
                    pos = pos + 1
                end
            end
            for j = 0, 3 do output[pos + (endianness == ByteOrder.BIG_ENDIAN and 7 - j or j)] = bit32.band(bit32.rshift(ll, (j * 8)), 0xFF) end
            for j = 4, 7 do output[pos + (endianness == ByteOrder.BIG_ENDIAN and 7 - j or j)] = bit32.band(bit32.rshift(lh, ((j - 4) * 8)), 0xFF) end
            pos = pos + 8
        elseif c == 'c' then
            local size = 0
            if (i > #fmt or not fmt:sub(i, i):match("%d")) then
                error("missing size for format option 'c'", 2)
            end
            while (i <= #fmt and fmt:sub(i, i):match("%d")) do
                if (size >= 0xFFFFFFFF / 10) then error("bad argument #1 to 'pack' (invalid format)", 2) end
                size = (size * 10) + tonumber(fmt:sub(i, i))
                i = i + 1
            end
            if (pos + size < pos or pos + size > 0xFFFFFFFF) then error("bad argument #1 to 'pack' (format result too large)", 2) end
            local str = expect(argnum, select(argnum, ...), "string")
            argnum = argnum + 1
            if (#str > size) then error(string.format("bad argument #%d to 'pack' (string longer than given size)", argnum - 1), 2) end
            if size > 0 then
                for j = 0, size - 1 do output[pos+j] = str:byte(j + 1) or 0 end
                pos = pos + size
            end
        elseif c == 'z' then
            local str = expect(argnum, select(argnum, ...), "string")
            argnum = argnum + 1
            for b in str:gmatch "." do if (b == '\0') then error(string.format("bad argument #%d to 'pack' (string contains zeros)", argnum - 1), 2) end end
            for j = 0, #str - 1 do output[pos+j] = str:byte(j + 1) end
            output[pos + #str] = 0
            pos = pos + #str + 1
        elseif c == 's' then
            local size = 0
            while (i <= #fmt and fmt:sub(i, i):match("%d")) do
                if (size >= 0xFFFFFFFF / 10) then error("bad argument #1 to 'pack' (invalid format)", 2) end
                size = (size * 10) + tonumber(fmt:sub(i, i))
                i = i + 1
            end
            if (size > 16) then
                error(string.format("integral size (%d) out of limits [1,16]", size), 2)
            elseif (size == 0) then size = 4 end
            local str = expect(argnum, select(argnum, ...), "string")
            argnum = argnum + 1
            if (#str >= math.pow(2, (size * 8))) then
                error(string.format("bad argument #%d to 'pack' (string length does not fit in given size)", argnum - 1), 2)
            end
            packint(#str, size, output, pos, 1, endianness, false)
            for j = size, #str + size - 1 do output[pos+j] = str:byte(j - size + 1) or 0 end
            pos = pos + #str + size
        elseif c == 'x' then
            output[pos] = 0
            pos = pos + 1
        elseif c == 'X' then
            if (i >= #fmt) then error("invalid next option for option 'X'", 2) end
            local size = 0
            local c = fmt:sub(i, i)
            i = i + 1
            if c:lower() == 'i' then
                while i <= #fmt and fmt:sub(i, i):match("%d") do
                    if (size >= 0xFFFFFFFF / 10) then error("bad argument #1 to 'pack' (invalid format)", 2) end
                    size = (size * 10) + tonumber(fmt:sub(i, i))
                    i = i + 1
                end
                if (size > 16 or size == 0) then
                    error(string.format("integral size (%d) out of limits [1,16]", size), 2)
                end
            else size = packoptsize(c, 0) end
            if (size < 1) then error("invalid next option for option 'X'", 2) end
            if (pos % math.min(size, alignment) ~= 0 and alignment > 1) then
                for j = 1, alignment do
                    if pos % math.min(size, alignment) == 0 then break end
                    output[pos] = 0
                    pos = pos + 1
                end
            end
        elseif c ~= ' ' then error(string.format("invalid format option '%s'", c), 2) end
    end
    return string.char(table.unpack(output))
end

--[[
 * string.packsize (fmt)
 *
 * Returns the size of a string resulting from string.pack with the given format.
 * The format string cannot have the variable-length options 's' or 'z'.
 ]]
local function packsize(fmt)
    local pos = 0
    local alignment = 1
    local i = 1
    while i <= #fmt do
        local c = fmt:sub(i, i)
        i = i + 1
        if c == '!' then
            local size = 0
            while i <= #fmt and fmt:sub(i, i):match("%d") do
                if (size >= 0xFFFFFFFF / 10) then error("bad argument #1 to 'pack' (invalid format)", 2) end
                size = (size * 10) + tonumber(fmt:sub(i, i))
                i = i + 1
            end
            if (size > 16) then error(string.format("integral size (%d) out of limits [1,16]", size), 2)
            elseif (size == 0) then alignment = 4
            else alignment = size end
        elseif isint[c] then
            local size = packoptsize(c, 0)
            if (pos % math.min(size, alignment) ~= 0 and alignment > 1) then
                for j = 1, alignment do
                    if pos % math.min(size, alignment) == 0 then break end
                    pos = pos + 1
                end
            end
            pos = pos + size
        elseif c:lower() == 'i' then
            local size = 0
            while i <= #fmt and fmt:sub(i, i):match("%d") do
                if (size >= 0xFFFFFFFF / 10) then error("bad argument #1 to 'pack' (invalid format)", 2) end
                size = (size * 10) + tonumber(fmt:sub(i, i))
                i = i + 1
            end
            if (size > 16) then
                error(string.format("integral size (%d) out of limits [1,16]", size))
            elseif (alignment > 1 and (size ~= 1 and size ~= 2 and size ~= 4 and size ~= 8 and size ~= 16)) then
                error("bad argument #1 to 'pack' (format asks for alignment not power of 2)", 2)
            elseif (size == 0) then size = 4 end
            if (pos % math.min(size, alignment) ~= 0 and alignment > 1) then
                for j = 1, alignment do
                    if pos % math.min(size, alignment) == 0 then break end
                    pos = pos + 1
                end
            end
            pos = pos + size
        elseif c == 'f' then
            if (pos % math.min(4, alignment) ~= 0 and alignment > 1) then
                for j = 1, alignment do
                    if pos % math.min(4, alignment) == 0 then break end
                    pos = pos + 1
                end
            end
            pos = pos + 4
        elseif c == 'd' or c == 'n' then
            if (pos % math.min(8, alignment) ~= 0 and alignment > 1) then
                for j = 1, alignment do
                    if pos % math.min(8, alignment) == 0 then break end
                    pos = pos + 1
                end
            end
            pos = pos + 8
        elseif c == 'c' then
            local size = 0
            if (i > #fmt or not fmt:sub(i, i):match("%d")) then
                error("missing size for format option 'c'", 2)
            end
            while i <= #fmt and fmt:sub(i, i):match("%d") do
                if (size >= 0xFFFFFFFF / 10) then error("bad argument #1 to 'pack' (invalid format)", 2) end
                size = (size * 10) + tonumber(fmt:sub(i, i))
                i = i + 1
            end
            if (pos + size < pos or pos + size > 0x7FFFFFFF) then error("bad argument #1 to 'packsize' (format result too large)", 2) end
            pos = pos + size
        elseif c == 'x' then
            pos = pos + 1
        elseif c == 'X' then
            if (i >= #fmt) then error("invalid next option for option 'X'", 2) end
            local size = 0
            local c = fmt:sub(i, i)
            i = i + 1
            if c:lower() == 'i' then
                while i <= #fmt and fmt:sub(i, i):match("%d") do
                    if (size >= 0xFFFFFFFF / 10) then error("bad argument #1 to 'pack' (invalid format)", 2) end
                    size = (size * 10) + tonumber(fmt:sub(i, i))
                    i = i + 1
                end
                if (size > 16 or size == 0) then
                    error(string.format("integral size (%d) out of limits [1,16]", size), 2)
                end
            else size = packoptsize(c, 0) end
            if (size < 1) then error("invalid next option for option 'X'", 2) end
            if (pos % math.min(size, alignment) ~= 0 and alignment > 1) then
                for j = 1, alignment do
                    if pos % math.min(size, alignment) == 0 then break end
                    pos = pos + 1
                end
            end
        elseif c == 's' or c == 'z' then error("bad argument #1 to 'packsize' (variable-length format)", 2)
        elseif c ~= ' ' and c ~= '<' and c ~= '>' and c ~= '=' then error(string.format("invalid format option '%s'", c), 2) end
    end
    return pos
end

--[[
 * string.unpack (fmt, s [, pos])
 *
 * Returns the values packed in string s (see string.pack) according to the format string fmt.
 * An optional pos marks where to start reading in s (default is 1).
 * After the read values, this function also returns the index of the first unread byte in s.
 ]]
local function unpack(fmt, str, pos)
    expect(1, fmt, "string")
    expect(2, str, "string")
    expect(3, pos, "number", "nil")
    if pos then
        if (pos < 0) then pos = #str + pos
        elseif (pos == 0) then error("bad argument #3 to 'unpack' (initial position out of string)", 2) end
        if (pos > #str or pos < 0) then error("bad argument #3 to 'unpack' (initial position out of string)", 2) end
    else pos = 1 end
    local endianness = ByteOrder.LITTLE_ENDIAN
    local alignment = 1
    local retval = {}
    local i = 1
    while i <= #fmt do
        local c = fmt:sub(i, i)
        i = i + 1
        if c == '<' or c == '=' then
            endianness = ByteOrder.LITTLE_ENDIAN
        elseif c == '>' then
            endianness = ByteOrder.BIG_ENDIAN
        elseif c == '!' then
            local size = 0
            while i <= #fmt and fmt:sub(i, i):match("%d") do
                if (size >= 0xFFFFFFFF / 10) then error("bad argument #1 to 'pack' (invalid format)", 2) end
                size = (size * 10) + tonumber(fmt:sub(i, i))
                i = i + 1
            end
            if (size > 16) then
                error(string.format("integral size (%d) out of limits [1,16]", size))
            elseif (size == 0) then alignment = 4
            else alignment = size end
        elseif isint[c] then
            if (pos + packoptsize(c, 0) > #str + 1) then error("data string too short", 2) end
            local res, ressz = unpackint(str, pos, packoptsize(c, 0), endianness, alignment, c:match("%l") ~= nil)
            retval[#retval+1] = res
            pos = pos + ressz
        elseif c:lower() == 'i' then
            local signed = c == 'i'
            local size = 0
            while (i <= #fmt and fmt:sub(i, i):match("%d")) do
                if (size >= 0xFFFFFFFF / 10) then error("bad argument #1 to 'pack' (invalid format)", 2) end
                size = (size * 10) + tonumber(fmt:sub(i, i))
                i = i + 1
            end
            if (size > 16) then
                error(string.format("integral size (%d) out of limits [1,16]", size), 2)
            elseif (size > 8) then
                error(string.format("%d-byte integer does not fit into Lua Integer", size), 2)
            elseif (size == 0) then size = 4 end
            if (pos + size > #str + 1) then error("data string too short", 2) end
            local res, ressz = unpackint(str, pos, size, endianness, alignment, signed)
            retval[#retval+1] = res
            pos = pos + ressz
        elseif c == 'f' then
            if (pos % math.min(4, alignment) ~= 0 and alignment > 1) then
                for j = 1, alignment do
                    if pos % math.min(4, alignment) == 0 then break end
                    pos = pos + 1
                end
            end
            if (pos + 4 > #str + 1) then error("data string too short", 2) end
            local res = unpackint(str, pos, 4, endianness, alignment, false)
            retval[#retval+1] = intBitsToFloat(res)
            pos = pos + 4
        elseif c == 'd' or c == 'n' then
            if (pos % math.min(8, alignment) ~= 0 and alignment > 1) then
                for j = 1, alignment do
                    if pos % math.min(8, alignment) == 0 then break end
                    pos = pos + 1
                end
            end
            if (pos + 8 > #str + 1) then error("data string too short", 2) end
            local lh, ll = 0, 0
            for j = 0, 3 do lh = bit32.bor(lh, bit32.lshift((str:byte(pos + j)), ((endianness == ByteOrder.BIG_ENDIAN and 3 - j or j) * 8))) end
            for j = 0, 3 do ll = bit32.bor(ll, bit32.lshift((str:byte(pos + j + 4)), ((endianness == ByteOrder.BIG_ENDIAN and 3 - j or j) * 8))) end
            if endianness == ByteOrder.LITTLE_ENDIAN then lh, ll = ll, lh end
            retval[#retval+1] = longBitsToDouble(lh, ll)
            pos = pos + 8
        elseif c == 'c' then
            local size = 0
            if (i > #fmt or not fmt:sub(i, i):match("%d")) then
                error("missing size for format option 'c'", 2)
            end
            while i <= #fmt and fmt:sub(i, i):match("%d") do
                if (size >= 0xFFFFFFFF / 10) then error("bad argument #1 to 'pack' (invalid format)") end
                size = (size * 10) + tonumber(fmt:sub(i, i))
                i = i + 1
            end
            if (pos + size > #str + 1) then error("data string too short", 2) end
            retval[#retval+1] = str:sub(pos, pos + size - 1)
            pos = pos + size
        elseif c == 'z' then
            local size = 0
            while (str:byte(pos + size) ~= 0) do
                size = size + 1
                if (pos + size > #str) then error("unfinished string for format 'z'", 2) end
            end
            retval[#retval+1] = str:sub(pos, pos + size - 1)
            pos = pos + size + 1
        elseif c == 's' then
            local size = 0
            while i <= #fmt and fmt:sub(i, i):match("%d") do
                if (size >= 0xFFFFFFFF / 10) then error("bad argument #1 to 'pack' (invalid format)", 2) end
                size = (size * 10) + tonumber(fmt:sub(i, i))
                i = i + 1
            end
            if (size > 16) then
                error(string.format("integral size (%d) out of limits [1,16]", size), 2)
            elseif (size == 0) then size = 4 end
            if (pos + size > #str + 1) then error("data string too short", 2) end
            local num, numsz = unpackint(str, pos, size, endianness, alignment, false)
            pos = pos + numsz
            if (pos + num > #str + 1) then error("data string too short", 2) end
            retval[#retval+1] = str:sub(pos, pos + num - 1)
            pos = pos + num
        elseif c == 'x' then
            pos = pos + 1
        elseif c == 'X' then
            if (i >= #fmt) then error("invalid next option for option 'X'", 2) end
            local size = 0
            local c = fmt:sub(i, i)
            i = i + 1
            if c:lower() == 'i' then
                while i <= #fmt and fmt:sub(i, i):match("%d") do
                    if (size >= 0xFFFFFFFF / 10) then error("bad argument #1 to 'pack' (invalid format)", 2) end
                    size = (size * 10) + tonumber(fmt:sub(i, i))
                    i = i + 1
                end
                if (size > 16 or size == 0) then
                    error(string.format("integral size (%d) out of limits [1,16]", size), 2)
                elseif (size == -1) then size = 4 end
            else size = packoptsize(c, 0) end
            if (size < 1) then error("invalid next option for option 'X'", 2) end
            if (pos % math.min(size, alignment) ~= 0 and alignment > 1) then
                for j = 1, alignment do
                    if pos % math.min(size, alignment) == 0 then break end
                    pos = pos + 1
                end
            end
        elseif c ~= ' ' then error(string.format("invalid format option '%s'", c), 2) end
    end
    retval[#retval+1] = pos
    return table.unpack(retval)
end

return {
    pack = pack,
    packsize = packsize,
    unpack = unpack
}
]=],
['youcube/LICENSE.txt'] = [[
                    GNU GENERAL PUBLIC LICENSE
                       Version 3, 29 June 2007

 Copyright (C) 2007 Free Software Foundation, Inc. <https://fsf.org/>
 Everyone is permitted to copy and distribute verbatim copies
 of this license document, but changing it is not allowed.

                            Preamble

  The GNU General Public License is a free, copyleft license for
software and other kinds of works.

  The licenses for most software and other practical works are designed
to take away your freedom to share and change the works.  By contrast,
the GNU General Public License is intended to guarantee your freedom to
share and change all versions of a program--to make sure it remains free
software for all its users.  We, the Free Software Foundation, use the
GNU General Public License for most of our software; it applies also to
any other work released this way by its authors.  You can apply it to
your programs, too.

  When we speak of free software, we are referring to freedom, not
price.  Our General Public Licenses are designed to make sure that you
have the freedom to distribute copies of free software (and charge for
them if you wish), that you receive source code or can get it if you
want it, that you can change the software or use pieces of it in new
free programs, and that you know you can do these things.

  To protect your rights, we need to prevent others from denying you
these rights or asking you to surrender the rights.  Therefore, you have
certain responsibilities if you distribute copies of the software, or if
you modify it: responsibilities to respect the freedom of others.

  For example, if you distribute copies of such a program, whether
gratis or for a fee, you must pass on to the recipients the same
freedoms that you received.  You must make sure that they, too, receive
or can get the source code.  And you must show them these terms so they
know their rights.

  Developers that use the GNU GPL protect your rights with two steps:
(1) assert copyright on the software, and (2) offer you this License
giving you legal permission to copy, distribute and/or modify it.

  For the developers' and authors' protection, the GPL clearly explains
that there is no warranty for this free software.  For both users' and
authors' sake, the GPL requires that modified versions be marked as
changed, so that their problems will not be attributed erroneously to
authors of previous versions.

  Some devices are designed to deny users access to install or run
modified versions of the software inside them, although the manufacturer
can do so.  This is fundamentally incompatible with the aim of
protecting users' freedom to change the software.  The systematic
pattern of such abuse occurs in the area of products for individuals to
use, which is precisely where it is most unacceptable.  Therefore, we
have designed this version of the GPL to prohibit the practice for those
products.  If such problems arise substantially in other domains, we
stand ready to extend this provision to those domains in future versions
of the GPL, as needed to protect the freedom of users.

  Finally, every program is threatened constantly by software patents.
States should not allow patents to restrict development and use of
software on general-purpose computers, but in those that do, we wish to
avoid the special danger that patents applied to a free program could
make it effectively proprietary.  To prevent this, the GPL assures that
patents cannot be used to render the program non-free.

  The precise terms and conditions for copying, distribution and
modification follow.

                       TERMS AND CONDITIONS

  0. Definitions.

  "This License" refers to version 3 of the GNU General Public License.

  "Copyright" also means copyright-like laws that apply to other kinds of
works, such as semiconductor masks.

  "The Program" refers to any copyrightable work licensed under this
License.  Each licensee is addressed as "you".  "Licensees" and
"recipients" may be individuals or organizations.

  To "modify" a work means to copy from or adapt all or part of the work
in a fashion requiring copyright permission, other than the making of an
exact copy.  The resulting work is called a "modified version" of the
earlier work or a work "based on" the earlier work.

  A "covered work" means either the unmodified Program or a work based
on the Program.

  To "propagate" a work means to do anything with it that, without
permission, would make you directly or secondarily liable for
infringement under applicable copyright law, except executing it on a
computer or modifying a private copy.  Propagation includes copying,
distribution (with or without modification), making available to the
public, and in some countries other activities as well.

  To "convey" a work means any kind of propagation that enables other
parties to make or receive copies.  Mere interaction with a user through
a computer network, with no transfer of a copy, is not conveying.

  An interactive user interface displays "Appropriate Legal Notices"
to the extent that it includes a convenient and prominently visible
feature that (1) displays an appropriate copyright notice, and (2)
tells the user that there is no warranty for the work (except to the
extent that warranties are provided), that licensees may convey the
work under this License, and how to view a copy of this License.  If
the interface presents a list of user commands or options, such as a
menu, a prominent item in the list meets this criterion.

  1. Source Code.

  The "source code" for a work means the preferred form of the work
for making modifications to it.  "Object code" means any non-source
form of a work.

  A "Standard Interface" means an interface that either is an official
standard defined by a recognized standards body, or, in the case of
interfaces specified for a particular programming language, one that
is widely used among developers working in that language.

  The "System Libraries" of an executable work include anything, other
than the work as a whole, that (a) is included in the normal form of
packaging a Major Component, but which is not part of that Major
Component, and (b) serves only to enable use of the work with that
Major Component, or to implement a Standard Interface for which an
implementation is available to the public in source code form.  A
"Major Component", in this context, means a major essential component
(kernel, window system, and so on) of the specific operating system
(if any) on which the executable work runs, or a compiler used to
produce the work, or an object code interpreter used to run it.

  The "Corresponding Source" for a work in object code form means all
the source code needed to generate, install, and (for an executable
work) run the object code and to modify the work, including scripts to
control those activities.  However, it does not include the work's
System Libraries, or general-purpose tools or generally available free
programs which are used unmodified in performing those activities but
which are not part of the work.  For example, Corresponding Source
includes interface definition files associated with source files for
the work, and the source code for shared libraries and dynamically
linked subprograms that the work is specifically designed to require,
such as by intimate data communication or control flow between those
subprograms and other parts of the work.

  The Corresponding Source need not include anything that users
can regenerate automatically from other parts of the Corresponding
Source.

  The Corresponding Source for a work in source code form is that
same work.

  2. Basic Permissions.

  All rights granted under this License are granted for the term of
copyright on the Program, and are irrevocable provided the stated
conditions are met.  This License explicitly affirms your unlimited
permission to run the unmodified Program.  The output from running a
covered work is covered by this License only if the output, given its
content, constitutes a covered work.  This License acknowledges your
rights of fair use or other equivalent, as provided by copyright law.

  You may make, run and propagate covered works that you do not
convey, without conditions so long as your license otherwise remains
in force.  You may convey covered works to others for the sole purpose
of having them make modifications exclusively for you, or provide you
with facilities for running those works, provided that you comply with
the terms of this License in conveying all material for which you do
not control copyright.  Those thus making or running the covered works
for you must do so exclusively on your behalf, under your direction
and control, on terms that prohibit them from making any copies of
your copyrighted material outside their relationship with you.

  Conveying under any other circumstances is permitted solely under
the conditions stated below.  Sublicensing is not allowed; section 10
makes it unnecessary.

  3. Protecting Users' Legal Rights From Anti-Circumvention Law.

  No covered work shall be deemed part of an effective technological
measure under any applicable law fulfilling obligations under article
11 of the WIPO copyright treaty adopted on 20 December 1996, or
similar laws prohibiting or restricting circumvention of such
measures.

  When you convey a covered work, you waive any legal power to forbid
circumvention of technological measures to the extent such circumvention
is effected by exercising rights under this License with respect to
the covered work, and you disclaim any intention to limit operation or
modification of the work as a means of enforcing, against the work's
users, your or third parties' legal rights to forbid circumvention of
technological measures.

  4. Conveying Verbatim Copies.

  You may convey verbatim copies of the Program's source code as you
receive it, in any medium, provided that you conspicuously and
appropriately publish on each copy an appropriate copyright notice;
keep intact all notices stating that this License and any
non-permissive terms added in accord with section 7 apply to the code;
keep intact all notices of the absence of any warranty; and give all
recipients a copy of this License along with the Program.

  You may charge any price or no price for each copy that you convey,
and you may offer support or warranty protection for a fee.

  5. Conveying Modified Source Versions.

  You may convey a work based on the Program, or the modifications to
produce it from the Program, in the form of source code under the
terms of section 4, provided that you also meet all of these conditions:

    a) The work must carry prominent notices stating that you modified
    it, and giving a relevant date.

    b) The work must carry prominent notices stating that it is
    released under this License and any conditions added under section
    7.  This requirement modifies the requirement in section 4 to
    "keep intact all notices".

    c) You must license the entire work, as a whole, under this
    License to anyone who comes into possession of a copy.  This
    License will therefore apply, along with any applicable section 7
    additional terms, to the whole of the work, and all its parts,
    regardless of how they are packaged.  This License gives no
    permission to license the work in any other way, but it does not
    invalidate such permission if you have separately received it.

    d) If the work has interactive user interfaces, each must display
    Appropriate Legal Notices; however, if the Program has interactive
    interfaces that do not display Appropriate Legal Notices, your
    work need not make them do so.

  A compilation of a covered work with other separate and independent
works, which are not by their nature extensions of the covered work,
and which are not combined with it such as to form a larger program,
in or on a volume of a storage or distribution medium, is called an
"aggregate" if the compilation and its resulting copyright are not
used to limit the access or legal rights of the compilation's users
beyond what the individual works permit.  Inclusion of a covered work
in an aggregate does not cause this License to apply to the other
parts of the aggregate.

  6. Conveying Non-Source Forms.

  You may convey a covered work in object code form under the terms
of sections 4 and 5, provided that you also convey the
machine-readable Corresponding Source under the terms of this License,
in one of these ways:

    a) Convey the object code in, or embodied in, a physical product
    (including a physical distribution medium), accompanied by the
    Corresponding Source fixed on a durable physical medium
    customarily used for software interchange.

    b) Convey the object code in, or embodied in, a physical product
    (including a physical distribution medium), accompanied by a
    written offer, valid for at least three years and valid for as
    long as you offer spare parts or customer support for that product
    model, to give anyone who possesses the object code either (1) a
    copy of the Corresponding Source for all the software in the
    product that is covered by this License, on a durable physical
    medium customarily used for software interchange, for a price no
    more than your reasonable cost of physically performing this
    conveying of source, or (2) access to copy the
    Corresponding Source from a network server at no charge.

    c) Convey individual copies of the object code with a copy of the
    written offer to provide the Corresponding Source.  This
    alternative is allowed only occasionally and noncommercially, and
    only if you received the object code with such an offer, in accord
    with subsection 6b.

    d) Convey the object code by offering access from a designated
    place (gratis or for a charge), and offer equivalent access to the
    Corresponding Source in the same way through the same place at no
    further charge.  You need not require recipients to copy the
    Corresponding Source along with the object code.  If the place to
    copy the object code is a network server, the Corresponding Source
    may be on a different server (operated by you or a third party)
    that supports equivalent copying facilities, provided you maintain
    clear directions next to the object code saying where to find the
    Corresponding Source.  Regardless of what server hosts the
    Corresponding Source, you remain obligated to ensure that it is
    available for as long as needed to satisfy these requirements.

    e) Convey the object code using peer-to-peer transmission, provided
    you inform other peers where the object code and Corresponding
    Source of the work are being offered to the general public at no
    charge under subsection 6d.

  A separable portion of the object code, whose source code is excluded
from the Corresponding Source as a System Library, need not be
included in conveying the object code work.

  A "User Product" is either (1) a "consumer product", which means any
tangible personal property which is normally used for personal, family,
or household purposes, or (2) anything designed or sold for incorporation
into a dwelling.  In determining whether a product is a consumer product,
doubtful cases shall be resolved in favor of coverage.  For a particular
product received by a particular user, "normally used" refers to a
typical or common use of that class of product, regardless of the status
of the particular user or of the way in which the particular user
actually uses, or expects or is expected to use, the product.  A product
is a consumer product regardless of whether the product has substantial
commercial, industrial or non-consumer uses, unless such uses represent
the only significant mode of use of the product.

  "Installation Information" for a User Product means any methods,
procedures, authorization keys, or other information required to install
and execute modified versions of a covered work in that User Product from
a modified version of its Corresponding Source.  The information must
suffice to ensure that the continued functioning of the modified object
code is in no case prevented or interfered with solely because
modification has been made.

  If you convey an object code work under this section in, or with, or
specifically for use in, a User Product, and the conveying occurs as
part of a transaction in which the right of possession and use of the
User Product is transferred to the recipient in perpetuity or for a
fixed term (regardless of how the transaction is characterized), the
Corresponding Source conveyed under this section must be accompanied
by the Installation Information.  But this requirement does not apply
if neither you nor any third party retains the ability to install
modified object code on the User Product (for example, the work has
been installed in ROM).

  The requirement to provide Installation Information does not include a
requirement to continue to provide support service, warranty, or updates
for a work that has been modified or installed by the recipient, or for
the User Product in which it has been modified or installed.  Access to a
network may be denied when the modification itself materially and
adversely affects the operation of the network or violates the rules and
protocols for communication across the network.

  Corresponding Source conveyed, and Installation Information provided,
in accord with this section must be in a format that is publicly
documented (and with an implementation available to the public in
source code form), and must require no special password or key for
unpacking, reading or copying.

  7. Additional Terms.

  "Additional permissions" are terms that supplement the terms of this
License by making exceptions from one or more of its conditions.
Additional permissions that are applicable to the entire Program shall
be treated as though they were included in this License, to the extent
that they are valid under applicable law.  If additional permissions
apply only to part of the Program, that part may be used separately
under those permissions, but the entire Program remains governed by
this License without regard to the additional permissions.

  When you convey a copy of a covered work, you may at your option
remove any additional permissions from that copy, or from any part of
it.  (Additional permissions may be written to require their own
removal in certain cases when you modify the work.)  You may place
additional permissions on material, added by you to a covered work,
for which you have or can give appropriate copyright permission.

  Notwithstanding any other provision of this License, for material you
add to a covered work, you may (if authorized by the copyright holders of
that material) supplement the terms of this License with terms:

    a) Disclaiming warranty or limiting liability differently from the
    terms of sections 15 and 16 of this License; or

    b) Requiring preservation of specified reasonable legal notices or
    author attributions in that material or in the Appropriate Legal
    Notices displayed by works containing it; or

    c) Prohibiting misrepresentation of the origin of that material, or
    requiring that modified versions of such material be marked in
    reasonable ways as different from the original version; or

    d) Limiting the use for publicity purposes of names of licensors or
    authors of the material; or

    e) Declining to grant rights under trademark law for use of some
    trade names, trademarks, or service marks; or

    f) Requiring indemnification of licensors and authors of that
    material by anyone who conveys the material (or modified versions of
    it) with contractual assumptions of liability to the recipient, for
    any liability that these contractual assumptions directly impose on
    those licensors and authors.

  All other non-permissive additional terms are considered "further
restrictions" within the meaning of section 10.  If the Program as you
received it, or any part of it, contains a notice stating that it is
governed by this License along with a term that is a further
restriction, you may remove that term.  If a license document contains
a further restriction but permits relicensing or conveying under this
License, you may add to a covered work material governed by the terms
of that license document, provided that the further restriction does
not survive such relicensing or conveying.

  If you add terms to a covered work in accord with this section, you
must place, in the relevant source files, a statement of the
additional terms that apply to those files, or a notice indicating
where to find the applicable terms.

  Additional terms, permissive or non-permissive, may be stated in the
form of a separately written license, or stated as exceptions;
the above requirements apply either way.

  8. Termination.

  You may not propagate or modify a covered work except as expressly
provided under this License.  Any attempt otherwise to propagate or
modify it is void, and will automatically terminate your rights under
this License (including any patent licenses granted under the third
paragraph of section 11).

  However, if you cease all violation of this License, then your
license from a particular copyright holder is reinstated (a)
provisionally, unless and until the copyright holder explicitly and
finally terminates your license, and (b) permanently, if the copyright
holder fails to notify you of the violation by some reasonable means
prior to 60 days after the cessation.

  Moreover, your license from a particular copyright holder is
reinstated permanently if the copyright holder notifies you of the
violation by some reasonable means, this is the first time you have
received notice of violation of this License (for any work) from that
copyright holder, and you cure the violation prior to 30 days after
your receipt of the notice.

  Termination of your rights under this section does not terminate the
licenses of parties who have received copies or rights from you under
this License.  If your rights have been terminated and not permanently
reinstated, you do not qualify to receive new licenses for the same
material under section 10.

  9. Acceptance Not Required for Having Copies.

  You are not required to accept this License in order to receive or
run a copy of the Program.  Ancillary propagation of a covered work
occurring solely as a consequence of using peer-to-peer transmission
to receive a copy likewise does not require acceptance.  However,
nothing other than this License grants you permission to propagate or
modify any covered work.  These actions infringe copyright if you do
not accept this License.  Therefore, by modifying or propagating a
covered work, you indicate your acceptance of this License to do so.

  10. Automatic Licensing of Downstream Recipients.

  Each time you convey a covered work, the recipient automatically
receives a license from the original licensors, to run, modify and
propagate that work, subject to this License.  You are not responsible
for enforcing compliance by third parties with this License.

  An "entity transaction" is a transaction transferring control of an
organization, or substantially all assets of one, or subdividing an
organization, or merging organizations.  If propagation of a covered
work results from an entity transaction, each party to that
transaction who receives a copy of the work also receives whatever
licenses to the work the party's predecessor in interest had or could
give under the previous paragraph, plus a right to possession of the
Corresponding Source of the work from the predecessor in interest, if
the predecessor has it or can get it with reasonable efforts.

  You may not impose any further restrictions on the exercise of the
rights granted or affirmed under this License.  For example, you may
not impose a license fee, royalty, or other charge for exercise of
rights granted under this License, and you may not initiate litigation
(including a cross-claim or counterclaim in a lawsuit) alleging that
any patent claim is infringed by making, using, selling, offering for
sale, or importing the Program or any portion of it.

  11. Patents.

  A "contributor" is a copyright holder who authorizes use under this
License of the Program or a work on which the Program is based.  The
work thus licensed is called the contributor's "contributor version".

  A contributor's "essential patent claims" are all patent claims
owned or controlled by the contributor, whether already acquired or
hereafter acquired, that would be infringed by some manner, permitted
by this License, of making, using, or selling its contributor version,
but do not include claims that would be infringed only as a
consequence of further modification of the contributor version.  For
purposes of this definition, "control" includes the right to grant
patent sublicenses in a manner consistent with the requirements of
this License.

  Each contributor grants you a non-exclusive, worldwide, royalty-free
patent license under the contributor's essential patent claims, to
make, use, sell, offer for sale, import and otherwise run, modify and
propagate the contents of its contributor version.

  In the following three paragraphs, a "patent license" is any express
agreement or commitment, however denominated, not to enforce a patent
(such as an express permission to practice a patent or covenant not to
sue for patent infringement).  To "grant" such a patent license to a
party means to make such an agreement or commitment not to enforce a
patent against the party.

  If you convey a covered work, knowingly relying on a patent license,
and the Corresponding Source of the work is not available for anyone
to copy, free of charge and under the terms of this License, through a
publicly available network server or other readily accessible means,
then you must either (1) cause the Corresponding Source to be so
available, or (2) arrange to deprive yourself of the benefit of the
patent license for this particular work, or (3) arrange, in a manner
consistent with the requirements of this License, to extend the patent
license to downstream recipients.  "Knowingly relying" means you have
actual knowledge that, but for the patent license, your conveying the
covered work in a country, or your recipient's use of the covered work
in a country, would infringe one or more identifiable patents in that
country that you have reason to believe are valid.

  If, pursuant to or in connection with a single transaction or
arrangement, you convey, or propagate by procuring conveyance of, a
covered work, and grant a patent license to some of the parties
receiving the covered work authorizing them to use, propagate, modify
or convey a specific copy of the covered work, then the patent license
you grant is automatically extended to all recipients of the covered
work and works based on it.

  A patent license is "discriminatory" if it does not include within
the scope of its coverage, prohibits the exercise of, or is
conditioned on the non-exercise of one or more of the rights that are
specifically granted under this License.  You may not convey a covered
work if you are a party to an arrangement with a third party that is
in the business of distributing software, under which you make payment
to the third party based on the extent of your activity of conveying
the work, and under which the third party grants, to any of the
parties who would receive the covered work from you, a discriminatory
patent license (a) in connection with copies of the covered work
conveyed by you (or copies made from those copies), or (b) primarily
for and in connection with specific products or compilations that
contain the covered work, unless you entered into that arrangement,
or that patent license was granted, prior to 28 March 2007.

  Nothing in this License shall be construed as excluding or limiting
any implied license or other defenses to infringement that may
otherwise be available to you under applicable patent law.

  12. No Surrender of Others' Freedom.

  If conditions are imposed on you (whether by court order, agreement or
otherwise) that contradict the conditions of this License, they do not
excuse you from the conditions of this License.  If you cannot convey a
covered work so as to satisfy simultaneously your obligations under this
License and any other pertinent obligations, then as a consequence you may
not convey it at all.  For example, if you agree to terms that obligate you
to collect a royalty for further conveying from those to whom you convey
the Program, the only way you could satisfy both those terms and this
License would be to refrain entirely from conveying the Program.

  13. Use with the GNU Affero General Public License.

  Notwithstanding any other provision of this License, you have
permission to link or combine any covered work with a work licensed
under version 3 of the GNU Affero General Public License into a single
combined work, and to convey the resulting work.  The terms of this
License will continue to apply to the part which is the covered work,
but the special requirements of the GNU Affero General Public License,
section 13, concerning interaction through a network will apply to the
combination as such.

  14. Revised Versions of this License.

  The Free Software Foundation may publish revised and/or new versions of
the GNU General Public License from time to time.  Such new versions will
be similar in spirit to the present version, but may differ in detail to
address new problems or concerns.

  Each version is given a distinguishing version number.  If the
Program specifies that a certain numbered version of the GNU General
Public License "or any later version" applies to it, you have the
option of following the terms and conditions either of that numbered
version or of any later version published by the Free Software
Foundation.  If the Program does not specify a version number of the
GNU General Public License, you may choose any version ever published
by the Free Software Foundation.

  If the Program specifies that a proxy can decide which future
versions of the GNU General Public License can be used, that proxy's
public statement of acceptance of a version permanently authorizes you
to choose that version for the Program.

  Later license versions may give you additional or different
permissions.  However, no additional obligations are imposed on any
author or copyright holder as a result of your choosing to follow a
later version.

  15. Disclaimer of Warranty.

  THERE IS NO WARRANTY FOR THE PROGRAM, TO THE EXTENT PERMITTED BY
APPLICABLE LAW.  EXCEPT WHEN OTHERWISE STATED IN WRITING THE COPYRIGHT
HOLDERS AND/OR OTHER PARTIES PROVIDE THE PROGRAM "AS IS" WITHOUT WARRANTY
OF ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING, BUT NOT LIMITED TO,
THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
PURPOSE.  THE ENTIRE RISK AS TO THE QUALITY AND PERFORMANCE OF THE PROGRAM
IS WITH YOU.  SHOULD THE PROGRAM PROVE DEFECTIVE, YOU ASSUME THE COST OF
ALL NECESSARY SERVICING, REPAIR OR CORRECTION.

  16. Limitation of Liability.

  IN NO EVENT UNLESS REQUIRED BY APPLICABLE LAW OR AGREED TO IN WRITING
WILL ANY COPYRIGHT HOLDER, OR ANY OTHER PARTY WHO MODIFIES AND/OR CONVEYS
THE PROGRAM AS PERMITTED ABOVE, BE LIABLE TO YOU FOR DAMAGES, INCLUDING ANY
GENERAL, SPECIAL, INCIDENTAL OR CONSEQUENTIAL DAMAGES ARISING OUT OF THE
USE OR INABILITY TO USE THE PROGRAM (INCLUDING BUT NOT LIMITED TO LOSS OF
DATA OR DATA BEING RENDERED INACCURATE OR LOSSES SUSTAINED BY YOU OR THIRD
PARTIES OR A FAILURE OF THE PROGRAM TO OPERATE WITH ANY OTHER PROGRAMS),
EVEN IF SUCH HOLDER OR OTHER PARTY HAS BEEN ADVISED OF THE POSSIBILITY OF
SUCH DAMAGES.

  17. Interpretation of Sections 15 and 16.

  If the disclaimer of warranty and limitation of liability provided
above cannot be given local legal effect according to their terms,
reviewing courts shall apply local law that most closely approximates
an absolute waiver of all civil liability in connection with the
Program, unless a warranty or assumption of liability accompanies a
copy of the Program in return for a fee.

                     END OF TERMS AND CONDITIONS

            How to Apply These Terms to Your New Programs

  If you develop a new program, and you want it to be of the greatest
possible use to the public, the best way to achieve this is to make it
free software which everyone can redistribute and change under these terms.

  To do so, attach the following notices to the program.  It is safest
to attach them to the start of each source file to most effectively
state the exclusion of warranty; and each file should have at least
the "copyright" line and a pointer to where the full notice is found.

    <one line to give the program's name and a brief idea of what it does.>
    Copyright (C) <year>  <name of author>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.

Also add information on how to contact you by electronic and paper mail.

  If the program does terminal interaction, make it output a short
notice like this when it starts in an interactive mode:

    <program>  Copyright (C) <year>  <name of author>
    This program comes with ABSOLUTELY NO WARRANTY; for details type `show w'.
    This is free software, and you are welcome to redistribute it
    under certain conditions; type `show c' for details.

The hypothetical commands `show w' and `show c' should show the appropriate
parts of the General Public License.  Of course, your program's commands
might be different; for a GUI interface, you would use an "about box".

  You should also get your employer (if you work as a programmer) or school,
if any, to sign a "copyright disclaimer" for the program, if necessary.
For more information on this, and how to apply and follow the GNU GPL, see
<https://www.gnu.org/licenses/>.

  The GNU General Public License does not permit incorporating your program
into proprietary programs.  If your program is a subroutine library, you
may consider it more useful to permit linking proprietary applications with
the library.  If this is what you want to do, use the GNU Lesser General
Public License instead of this License.  But first, please read
<https://www.gnu.org/licenses/why-not-lgpl.html>.
]],
}
 
local function fail(message)
error(message, 0)
end
 
local function ensureDir(path)
if path == "" or path == "/" then return end
if fs.exists(path) then
if not fs.isDir(path) then fail("La ruta no es una carpeta: " .. path) end
return
end
local parent = fs.getDir(path)
if parent ~= path and parent ~= "" then ensureDir(parent) end
fs.makeDir(path)
end
 
local function writeFile(path, content)
ensureDir(fs.getDir(path))
local handle, reason = fs.open(path, "w")
if not handle then fail("No se pudo escribir " .. path .. ": " .. tostring(reason)) end
local ok, writeError = pcall(handle.write, content)
handle.close()
if not ok then fail("Error escribiendo " .. path .. ": " .. tostring(writeError)) end
end
 
local function install()
local stamp = tostring(os.epoch and os.epoch("utc") or os.time())
local stage = "/.xp_pocket_mobile_stage_" .. stamp
local backup = "/xp_mobile_backups/" .. stamp
if fs.exists(stage) then fail("Ya existe el directorio temporal: " .. stage) end
ensureDir(stage)
 
for path, content in pairs(FILES) do
writeFile(fs.combine(stage, path), content)
end
 
local installed, backedUp = {}, {}
local ok, reason = pcall(function()
for path in pairs(FILES) do
local destination = "/" .. path
local stagedFile = fs.combine(stage, path)
ensureDir(fs.getDir(destination))
if fs.exists(destination) then
if fs.isDir(destination) then fail("No se reemplazara una carpeta: " .. destination) end
local saved = fs.combine(backup, path)
ensureDir(fs.getDir(saved))
fs.move(destination, saved)
backedUp[#backedUp + 1] = { destination, saved }
end
fs.move(stagedFile, destination)
installed[#installed + 1] = destination
end
end)
 
if not ok then
for i = #installed, 1, -1 do
if fs.exists(installed[i]) then fs.delete(installed[i]) end
end
for i = #backedUp, 1, -1 do
local item = backedUp[i]
if fs.exists(item[2]) and not fs.exists(item[1]) then fs.move(item[2], item[1]) end
end
if fs.exists(stage) then fs.delete(stage) end
fail("Instalacion cancelada; se restauraron los archivos anteriores. " .. tostring(reason))
end
 
if fs.exists(stage) then fs.delete(stage) end
print("Android Mobile instalado correctamente.")
print("Archivos instalados: " .. tostring(#installed))
if #backedUp > 0 then
print("Copia de seguridad de los archivos previos: " .. backup)
end
print("Reinicia con: reboot")
end
 
local ok, reason = pcall(install)
if not ok then
printError("Android Mobile: " .. tostring(reason))
end
 
