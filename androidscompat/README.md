# CC:Androids Compatibility

Addon Forge 1.20.1 for the modpack in this workspace.

## What it fixes

CC:Androids exposes `attack()` as a melee task. NTGL, however, already has a
server-side `GunAttackGoal` for `PathfinderMob` entities holding an `IWeapon`.
This addon supplies the missing vanilla target to CC:Androids. NTGL then
handles aiming, shooting, ammo and reloading without Lua trying to emulate a
mouse click.

Create: Gunsmithing weapons are NTGL weapons, so they are covered automatically.
The adapter is intentionally based on NTGL's `IWeapon` interface rather than a
hard-coded list of item IDs, so other NTGL weapon mods are covered too.

## Other mods in this pack

Android targeting uses Minecraft's shared `Enemy` interface, so hostile mobs
from content mods such as Alex's Mobs, Vampirism, Werewolves and Ars Nouveau
can be selected without a separate hard dependency on each mod. Equipment
handling accepts registered armor items, and backpack storage uses Forge item
capabilities; Curios and Sophisticated Backpacks have dedicated optional
adapters. Those integrations use shared interfaces instead of item IDs, so
other mods that expose armor or backpack inventories through those interfaces
can work without a new hard dependency. `/androidscompat compat` lists
installed optional mod IDs; it reports detection and does not claim each
listed mod has a dedicated adapter.

The AI commander is provided by this addon and operates through a CC:Androids
Command Android. When that Android has the addon's commander marker, its death
drop list is filtered so a command block is not dropped.

## Aircraft combat

Immersive Aircraft 1.5.0 and Man of Many Planes 0.2.1 are supported through
their shared `VehicleEntity` API. Put an aircraft weapon in the aircraft's
weapon inventory and give it the required ammunition. Then:

1. Put the Android and an empty aircraft close to the player.
2. Run `/androidscompat mount`.
3. The Android becomes the first passenger, flies toward the nearest enemy
   aircraft when one is nearby, or toward hostile ground mobs otherwise.
4. Mounted aircraft weapons are fired server-side. A bomb bay drops bombs at
   ground targets; guns and other mounted weapons can engage aircraft.

Use `/androidscompat dismount` to remove the nearest mounted Android. The
aircraft AI only takes control when an Android is the first passenger, so
player-piloted aircraft are left alone.

### Aircraft roles

After mounting, select one role as the player:

- `/androidscompat mode combat` — engages nearby aircraft first, then hostile
  ground mobs, using all mounted weapons.
- `/androidscompat mode bomber` — ignores aircraft and fires only mounted Bomb
  Bays at hostile ground mobs.
- `/androidscompat mode transport` — does not target or fire; it keeps the
  aircraft moving for cargo transport.

The matching Lua launchers are `piloto_combate.lua`,
`bombardero.lua` and `transportador_cargas.lua`. They provide the role
status/refuelling loop; the Java addon remains responsible for flight and
weapons.

## Command console, squads and field support

Press **G** in game to open the client command console. The server remains
authoritative and slash commands require operator permission. The console can
toggle the AI commander, order nearby Androids, manage aircraft, and request
capture, intelligence, repair, and base actions.

Assign the nearest Android to a squad and order all Androids with that squad
within 256 blocks:

```text
/androidscompat squad <name>
/androidscompat squad <name> order <hold|patrol|guard|follow|rescue|logistics|return>
```

Manage the nearest Android's saved home position with
`/androidscompat home set|show|clear`. The saved position is used for return orders and as a
commander patrol center. To repair a damaged Android, stand within eight
blocks and run `/androidscompat repair`; it consumes one iron ingot, restores
up to 10 health, and clears harmful status effects.

## Allied Village Guard

Loaded villages near players can raise a persistent guard garrison with one
chief and five randomized soldier skins, using the supplied MinecraftSkins.com
army-villager, urban, Irish and revolutionary textures. The chief uses the
separate commander skin. The guard is allied by default. Player damage against
guards or village residents increases a shared world tension meter; at 100%,
every village recognizes the aggression and the guard becomes hostile.
`/aldea estado` reports current diplomacy. The server setting
`villageDefendersEnabled` enables the system and `villageSkirmishChance`
controls the chance of a random guard-versus-Puente Norte battle during a
village scan.

Separately, a Puente Norte siege or occupation puts that village's guard into a
local dynamic war; the conflict spreads as Puente Norte sieges or occupies
other villages. Random events can trigger a small skirmish near an active
player.

With Create: Gunsmithing and NTGL present, chiefs use a CGS Flintlock with a
Stock and Musket Barrel (`flintlock_long_barrel`) attachment; guards choose a
role-weighted CGS weapon and carry a limited ammunition reserve. Crossbows are
used when CGS/NTGL is
unavailable. Nearby aircraft with weapons can be assigned as village air
support during skirmishes. Unarmed compatible aircraft can fly a two-way
logistics route between known villages.

Players can take a contract with `/aldea mision aceptar`, check progress with
`/aldea mision`, and claim emeralds with `/aldea mision cobrar`. Contracts
request a Puente Norte soldier at a dynamic rank plus five members of the
same subfaction. `/aldea reclutar` hires up to five guards; the emerald price
scales with world tension and the number already hired. Recruits follow their
owner outside combat and defend them once village diplomacy turns hostile.

## Puente Norte hostile faction

Puente Norte adds custom hostile raiders that use the supplied 64x64 soldier
and boss skins. Soldier skins are selected per entity and synchronized to
clients, so line/support troops can visibly differ; boss skin selection is
also synchronized and follows the boss subfaction. Support soldiers provide
short, subfaction-specific buffs to nearby allies. Faction soldiers refresh
their target choice every half-second, prioritize their Nemesis player when
applicable, and can retarget hostile Androids if no player is available.
When compatible CGS weapons are present, raiders choose among the supported
registered weapons instead of all receiving the first registry match. The server owns the faction simulation: all connected players
share the village raid schedule, Nemesis records remain attached to each
player, and a village can only start one automatic raid per configured interval
even when several players are nearby. Faction mobs and raids run in the normal
server tick and continue independently of any one client. They activate around
loaded villages near players; unloaded areas are not simulated. A new world has
a grace period of 36,000 ticks (1.5 in-game days) before any faction raid or
base can appear, including manual operator commands. After that, a raid can
start every 20 in-game minutes when a player is within 160 blocks, and the
faction builds a stone and timber outpost nearby. Operators can trigger the
systems directly with `/androidscompat puentenorte raid` and
`/androidscompat puentenorte base`. A village siege advances only while at
least three faction attackers outnumber nearby defenders; a solo player,
iron golems, and villagers all slow or reverse the assault. An uncontested
occupation takes at least 8 minutes 20 seconds without opposition, only one
village can be under siege at once, and the faction can hold at most three
villages by default. Occupation and
cooldowns persist across server restarts. Players can liberate an occupied
village by clearing the nearby garrison and staying in the area; check the
nearest village with `/androidscompat puentenorte status`. The conquest update
rate, points gained/lost, minimum attackers, active siege cap, occupation cap
and liberation cooldown are server-configurable (`villageConquest`,
`conquestUpdateTicks`, `conquestPointsPerUpdate`,
`conquestRepelPointsPerUpdate`, `conquestMinimumRaiders`,
`maxActiveVillageSieges`, `maxOccupiedVillages`, and
`conquestCooldownTicks`). When NTGL and Create: Gunsmithing are installed,
soldiers choose randomly from every registered CGS item implementing NTGL's
`IWeapon`, and have a 70% chance to spawn with one compatible NTGL attachment.
Crossbows remain the fallback if no supported CGS weapon is available.

Puente Norte also raids a nearby online player's saved respawn base when the
player has a bed/respawn point in that dimension. Raiders spawn around that
position, search containers nearby, and take up to the configured number of
valuable items and aircraft components per raid. Successful attacks can also
steal one valuable item from the player's carried inventory on a cooldown;
raiders drop stolen goods when killed or can carry them back to a faction
stockpile.

Faction bases are assembled over time rather than placed in one tick. Each
raid can choose a different nearby site and build a watchtower, palisade post,
or field camp from the ground up; builders work around the site and blocks are
placed live without replacing existing solid blocks. A post has a 55% default
chance to contain a captive rebel villager behind iron bars and a fence gate;
interact with the captive to open the cell and release them. Construction speed
and prisoner chance are controlled by `outpostBuildStepTicks` and
`outpostPrisonerChance`. Rogue and basic hostile Android builders work around
the site. These are new
AndroidsCompat entity types with separate red/gunmetal colorways and no player
spawn eggs or crafting recipes; CC:Androids' original Android and commander
recipes are unchanged. A completed base keeps a stockpile and processes
available Immersive Aircraft and Man of Many Planes crafting recipes every 10
seconds. Raiders bring stolen aircraft parts and ingredients to the stockpile;
when a complete aircraft recipe is available, the faction crafts and spawns
that aircraft with a rogue pilot. This requires the relevant items to be
available in player-base containers and the base area to remain loaded.

When Immersive Aircraft is installed (including compatible Man of Many Planes
vehicles), a raid can assign an air-support pilot to an unoccupied compatible
aircraft within 128 blocks of the village, if it has a weapon mounted. The
aircraft joins the fight against players and player aircraft. The server config
can disable air support or change its per-raid chance. Puente Norte does not
create aircraft or equip them with weapons; an available equipped airframe is
required.

Server owners can tune all faction behavior in the world's
`serverconfig/androidscompat-server.toml`, under `[puenteNorte]`. Settings
include faction/raid/base/boss/Nemesis/adaptation/CGS toggles, raid interval and
scan frequency, activation and roster distances, squad cap and strength
multiplier, boss chance, maximum Nemesis rank, rank-up rate, Stalker and
mini-boss ranks, health/armor/speed growth, adaptation hit threshold and
resistance cap, independent fire/projectile/melee adaptations, live outpost
build speed and captive-rebel chance, air-support toggle and chance, grace
period in world ticks,
player-base raid and theft toggles, theft chance, maximum base loot per raid,
aircraft production interval and cap per base, and separate line/support and
Stalker ratios for each subfaction. The default grace period is 36,000 ticks;
set `gracePeriodTicks = 0` to remove it. Clients do not control these server
rules.

Deaths at the hands of the faction are remembered per player. Each soldier can
learn and retain separate resistance profiles for fire, projectiles and melee;
learning one no longer erases another. Personal Nemeses keep hunting their
marked player and warn them when they get close. Rank-ups still improve
durability and roles but no longer refill the Nemesis to full health. The live
roster shows learned profiles and personal tracking. Raids can bring
El Superintendente (who reinforces nearby troops), Filemón (who enters a
berserk state at low health), or Professor Bacterio (who poisons and slows
nearby targets). Named bosses escalate through two health-triggered combat
phases, gain subfaction-specific temporary abilities, and announce phase
changes to nearby players. After repeated deaths, higher Nemesis ranks gain health and
armor, and Mortadelo can appear as the final boss. Each boss leads a squad with its own
size and ratio of line/support soldiers; squad size and durability scale with
Nemesis rank. A soldier that kills a player persists as a Nemesis: each kill
raises its rank, then evolves into a stalking hunter and mini-boss at the
server-configured rank thresholds. Soldiers can learn resistance after
repeated
hits from fire, projectiles, or melee. Press **N** to open the configurable
Nemesis roster; it lists nearby active troops, bosses, adaptations, and recent
defeated Nemeses. Soldier and boss textures are stored under `assets/androidscompat/textures/entity`.

When Patchouli is installed, the addon adds a bilingual AndroidsCompat
operations category to the existing Advanced Peripherals manual
(`advancedperipherals:manual`). It covers squad commands, saved bases, field
repairs, combat, aircraft, espionage, capture, and TTS voice configuration.

## Android speech over Simple Voice Chat

When Simple Voice Chat 2.6.0 or newer is installed on the server and clients,
an operator can make the nearest Android speak through proximity voice chat:

```text
/androidscompat speak <texto>
```

Set `tts.endpoint` in the world's
`serverconfig/androidscompat-server.toml` to an HTTP(S) TTS service. The addon
sends the text as a UTF-8 `text/plain` POST body and expects
a WAV response containing PCM audio. It converts that audio to mono 48 kHz
16-bit PCM, then Simple Voice Chat plays it from the Android's position. Speech
is limited to 500 input characters and 30 seconds of generated audio. The TTS
service is not bundled; the endpoint is left blank by default. `timeoutMs` and
`voiceDistance` in the same config control the service timeout and hearing
range.

This is server-generated speech broadcast in voice chat. Player microphone
capture and speech recognition remain Simple Voice Chat/client features and
are not changed by this addon.

## Build

Run `gradlew.bat build` from this directory. The output JAR is written to
`build/libs/androidscompat-1.0.0.jar`.


https://bmclapi2.bangbang93.com/maven/ ese es el repo de libraries de gradle