# Mios Coppergolems Habour: Harbours & Railways

An add-on for [Mios Coppergolems](../README.md): **harbours, routes and golem captains**. You build a harbour with a **Kontor** (the warehouse and office) and **Mooring Posts**, set a **route** to another harbour (what to ship, how much, once or over and over) and copper golem **captains** (and **dockhands**, if you like) do the rest, in plain sight: they fetch the goods from the kontor, load them into **chest boats**, climb aboard, sail to the other harbour, moor, and carry the cargo into the other kontor.

Needs Minecraft 26.3, Fabric, Fabric API and Mios Coppergolems. Same-dimension only.

---

## Quick start

1. **Build two harbours** by the same water. In each, place a **Harbour Kontor** on land and a few **Mooring Posts** at the water's edge (within 24 blocks of the kontor).
   - Kontor: copper ingots, a book, a barrel and planks (`CBC / PRP / PPP`). Mooring Post (×2): string, planks, copper ingot, stacked.
   - Rename a kontor in an anvil to give the harbour a name.
2. **Put chest boats on the water** next to the mooring posts of the harbour that sends goods. One boat per captain.
3. **Make captains.** Right-click a copper golem with a **spyglass**. Then, with an empty hand, **sneak + right-click the golem** and **right-click the kontor** of its harbour. Take the spyglass back with an empty hand while it is idle.
4. **Make dockhands.** Right-click a golem with a piece of **string**, then link it to the kontor the same way. Dockhands carry all the cargo between the kontor and the boats; the captain never does. Every harbour that sends or receives needs at least one.
5. **Fill the kontor** with the goods (by hand, hoppers, sorter golems…).
6. **Set a route.** **Sneak + right-click the kontor** to open the route book, press **+**, pick the destination with the arrows, then click a cargo slot while holding an item and set the amount with the buttons.

The route runs by itself from then on.

---

## The route book

| Setting | Meaning |
|---|---|
| **Destination** | Any other kontor in the same dimension (arrows cycle through the known harbours, by name). |
| **Cargo** | Up to 9 lines: item + amount. Click a slot with an item in your hand (you keep the item), then use `-64 -16 -1 +1 +16 +64` or **all**. Right-click a slot or press **Remove** to delete a line. Shift-click an item in your inventory adds it. **No cargo = everything in the kontor.** |
| **Runs** | `-` / `+` set how many runs; **forever** repeats endlessly. One run = everything on the cargo list, however many boats it takes. |
| **Every** | Time between the start of two runs: straight away, 1, 3, 5, 10, 20 (a Minecraft day), 60 minutes. |
| **Start** | **Timer**, or **Redstone**: a rising edge on the kontor starts one run. |
| **Wait for full cargo** | *Yes*: a run only starts when the kontor holds all the amounts. *No*: it sends what is there. |
| **Active / Paused** | Pauses the route without losing its settings. |
| **Run now** / **Stop** | Starts a run at once / calls the current run off. |

The screen shows what the route is waiting for (timer, redstone, goods, a captain, a boat, a berth…) and how many boats it has sailed and items delivered.

**Where is my captain?** The bottom of the route book says what the route's captain is doing and where it is: *sailing out, 140 blocks north-east of the kontor* (with *seen N s ago* if it has not reported for a while, and *+1 more* for further captains). **Locate** makes the route's captains glow for 30 seconds (through walls, as far as they are loaded), draws a trail of particles from you to the nearest one and tells you its coordinates. Up to 8 routes per kontor.

---

## How a voyage works

1. **Dispatch.** Every second a kontor looks at its routes. A route that is due starts a *run*; the kontor then sends boats off one at a time: each gets a **manifest** (up to 27 slots' worth: a chest boat's capacity), an idle **captain** and a free **boat**. A big run takes several boats; if there are fewer captains or boats than needed, the rest follows when they are back. Goods promised to one boat are not promised again to another.
2. **Loading.** The captain never carries cargo. A **dockhand** of the harbour walks to the kontor, takes up to **16 items** (a golem's hand), walks to the boat and puts them in, and repeats until the manifest is done. Several dockhands can work on one boat, and one dockhand serves one boat at a time. The captain waits by the boat and casts off when the last load is in. A harbour needs at least one dockhand, or no boat is sent (the route book says *No dockhand to carry the cargo*).
3. **Sailing.** The captain sits in the boat and sails it. It plans a way over the water on its own (an A\* search over the chunks around the boat) and sails around headlands and bays that lead nowhere. While a boat is at sea, the chunks around it stay loaded (like couriers do), so voyages go on when you are far away.
4. **Mooring.** At the destination it looks for a free berth: water next to a mooring post that no other boat lies on (with no post, water around the kontor). Boats that arrive together wait for each other.
5. **Unloading.** The captain steps ashore and waits while the dockhands of the destination harbour carry the cargo, 16 at a time, into the kontor. If the kontor stays full for a minute, the cargo goes **back home again**; if the destination has no dockhand at all, the captain turns back with it after a minute.
6. **Home.** The empty boat sails back and moors at the home harbour; the captain reports to its kontor (the route's trip counter goes up) and is ready for the next boat.

If a captain cannot find a way through, it turns back and brings the cargo home. If the boat is destroyed, the voyage is called off.

### Dockhands
A dockhand is a golem holding **string** and linked to a kontor (or a station), like a captain (sneak + right-click the golem, then right-click the kontor; take the string back with an empty hand while it is idle). Every second it looks for a boat or train of its own harbour that needs carrying: one waiting to be loaded, or one that has moored there with cargo. It claims it, carries the goods 16 at a time and lets go when the job is done. It never gets into a boat and never sails. It starts loading by day, but unloading a boat that has already arrived goes on at night. Dockhands prefer boats nobody is working on yet.

### Captains are working golems
- Dockhands too. They pay the usual running costs of Mios Coppergolems (energy, rust, they need a Charging Pad). They only **start** a voyage by day and with energy, but finish one at night. A captain at sea does not go to charge or hop into other carts or boats.
- You see the work: the golem reaches out with its arm at the kontor and at the boat, walks with the item in its hand, sits in the boat.

### Harbours keep working while you are away *(designed, not yet tested at a distance)*
A harbour that has a route that could run is woken up about every 30 seconds: its chunks load for a few seconds, so its kontor can see that a route is due and send a boat off. Voyages carry their own chunk loading. (This is a mild chunk loader: a harbour with active routes is loaded briefly, regularly. Pause the routes to stop it.)

---

## Design decisions

These were my calls to make while building; change them if you disagree.

- **Captains never carry, dockhands do.** A voyage is one captain, one boat (or train) and one manifest, and "multiple chest boats need multiple captains" falls out of it. Dockhands work on the manifest of a waiting captain. Every harbour needs at least one, and dockhands never sail.
- **Chest boats are not built or bought by the mod.** You put them on the water; they are "claimed" by a harbour when they lie within a few blocks of its mooring posts (or its kontor, if there are none), are empty of passengers and not already taken.
- **A harbour is a kontor plus the mooring posts within 24 blocks.** Berths are the water next to the posts. A kontor without any post uses the water within 8 blocks of itself, so a minimal harbour is just a kontor and a boat.
- **Routes live in the kontor they start from.** The destination is another kontor, picked from the registry of all kontors (saved with the world, so far-away harbours can be chosen).
- **One voyage = one captain, one boat, one-way cargo.** Boats come back empty; set up a second route the other way for return cargo.
- **Same water level only.** The pilot does not handle locks, waterfalls or ice; it needs a boat's width (3 blocks including the margin to the shore) and 2 blocks of air above the water.
- **No custom networking.** The route book uses ghost slots and data slots like the Survey Table, so it is as robust as vanilla menus.
- **Boats and minecarts are for the add-on only.** Golems no longer ride them on their own (the base mod used to let any golem on a long walk climb into a boat or cart); only captains and conductors do, and only here.

### Changes to the base mod
`GolemJob` got a new optional method `busy(CopperGolem)` and `MiosCoppergolemsApi.isBusy(golem)` for jobs that must not be interrupted (a captain at sea, a dockhand carrying cargo). `ChargeBehavior` skips busy golems. The base mod's `RideBehavior` (golems riding boats and minecarts on long trips) is removed. `CopperChestMinecart.setChain(ItemStack)` lets a conductor store the chain it couples with, so the chain comes back when the cart is unchained. Nothing else in the base mod changed.

---

## Railways

The same idea on rails: a **Railway Station** (it uses the kontor's code: storage with right-click, route book with sneak + right-click, rename it in an anvil) sends **trains of copper chest minecarts** driven by **conductors** to another station.

1. Lay a track between two stations. Put a **Railway Station** (copper ingots, a book, a barrel and rails: `CBC / RAR / CCC`) within 24 blocks of the track at each end.
2. Put **copper chest minecarts** on the rails near the sending station. A single cart is a train of one.
3. **Conductors:** right-click a golem with a **clock**, then sneak + right-click it and right-click the station. **Dockhands** (string) work at stations just like at harbours.
4. Put some **chains** (any iron or copper chain) into the station's storage if you want longer trains.
5. Set a route in the station's route book. Stations only send to other stations.

How a trip goes: the station picks the free train near it with the most room; if the cargo needs more room, the conductor **fetches chains from the station and couples free carts standing within 8 blocks** to the end of the train, one by one (up to five carts, each with its own chain, which comes out of the station's storage). It then sits in the lead cart while the dockhands load all carts. At the start of each leg it checks which way the carts lie: if they are ahead of the train it climbs out, the chains are moved so that the last cart leads, and it takes that cart's seat (**the train turns round on plain track**, no loop needed). It drives to the rail closest to the other station, waits while that station's dockhands unload the carts, turns the empty train round and drives it home. The carts stay coupled for the next run.

The track is read from the rails as they are: curves and slopes are fine. **Junctions are not switched**: a train only takes the way the rail blocks point. Keep other carts off the track.

Locate and the fleet line in the route book work for conductors too.

---

## Commands (admins, permission level 2)

| Command | Does |
|---|---|
| `/harbour list` | Lists the harbours in this dimension |
| `/harbour info <kontor>` | Routes, their status, and the captains of one kontor |
| `/harbour route <kontor> add <destination>` | Adds a route (everything, once) |
| `/harbour route <kontor> cargo <id> <item> <amount>` | Sets a one-line cargo list (0 = all) |
| `/harbour route <kontor> run <id>` | Starts a run now |
| `/harbour route <kontor> remove <id>` | Deletes a route |

`<kontor>` and `<destination>` are block coordinates.

---

## Testing

`./gradlew :habour:runClientGameTest` launches the game and plays a scripted world: two harbours at the ends of a 60 block channel with a headland in between, boats and captains, a first voyage with 100 iron, then a 600 item shipment that needs two boats at once, then a shipment with dockhands at both harbours (every tick it checks that no item is lost or duplicated, that no dockhand gets into a boat, and that the captain can be located). It takes screenshots of the harbours and the route book into `habour/run/screenshots/`. Output lines start with `[HABOUR-TEST]`.

A second scripted test plays the railway (`HABOUR_RAIL=1 ./gradlew :habour:runClientGameTest`, output lines start with `[RAIL-TEST]`): two stations 70 blocks apart on a track with a bend and a hill, one conductor, four dockhands, two free carts and one chain. An 800 item shipment needs two carts, so the conductor must couple one; the train is turned at both ends. Every tick the test checks that no snowball or chain is lost or duplicated, that no cart leaves the track and that no dockhand gets into a cart.

### What the scripted test has (and has not) covered
Covered, in a real world: harbour registration, dockhands loading and unloading (with item conservation checked every tick), locating a captain in the route book, loading, boarding, sailing around a headland, mooring, unloading, the way home and the trip counter, two captains working at once on one route, the route book screen, and breaking a kontor (goods drop, harbour is forgotten).

**Not covered yet** (written to work, never run; for railways also: junctions, more than two carts, a train longer than the loaded chunks, powered or detector rails, a full destination station, a blocked track): a dedicated server (not started: it needs the Minecraft EULA accepted); the player being far away, so the harbour wake-up and the boat's own chunk loading; voyages over several hundred blocks; the redstone start; a full destination kontor (the captain bringing cargo home); the pilot giving up and turning back; saving and reloading in the middle of a voyage; night time; routes between harbours that are far apart. Please try these first.

### Manual checklist
- [ ] Place a kontor, open it (storage) and sneak-open it (routes). Rename it in an anvil: the harbour name changes in the route book of other kontors.
- [ ] Break the kontor: contents drop, the harbour disappears from the destination list.
- [ ] Captain: spyglass on a golem, link, get the spyglass back; linking a golem without a spyglass shows a message.
- [ ] A route with no cargo list ships everything; with a list, only that.
- [ ] *Wait for full cargo* off ships what is there; on waits ("Waiting for goods").
- [ ] Redstone start: a pulse starts exactly one run.
- [ ] Dockhands: link one with string at each harbour; they load and unload while the captain waits. Take the string back (idle dockhand only).
- [ ] Locate: with a captain out, press Locate in the route book: it glows, a particle trail points to it, coordinates appear.
- [ ] Railway: build the track and two stations, a clock conductor, dockhands and carts; send a shipment that needs two carts and watch the coupling, the loading, the ride, the turn-round and the way home. Try a slope and a bend.
- [ ] Two boats, one captain: the second boat's load follows after the first has come back. Two boats, two captains: they sail together.
- [ ] Fill the destination kontor: the captain waits a minute, then brings the cargo home.
- [ ] Log out in the middle of a voyage and come back: it goes on.
- [ ] Walk away during a route: it still runs, and nothing explodes when you come back.

---

## Build

```
.\gradlew.bat :habour:build   # habour/build/libs/mioscoppergolems-habour-0.1.0.jar
.\gradlew.bat :habour:runClient   # the game with the base mod and this add-on
```

Dockhands (and conductors) share one set of walking and carrying code (`captain/DockWork`); `captain/Vessel` is what they carry into: a boat or a train.

Resources (models, textures, language files, recipes, advancements, handbook chapters) are generated by `tools/gen_habour_resources.py habour/src/main/resources`.

## Package layout
```
habour/src/main/java/dev/mioscoppergolems/habour/
  Habour.java       entry point
  kontor/           KontorBlock, StationBlock, KontorBlockEntity (storage, routes, dispatching for both)
  dock/             MooringPostBlock
  route/            Route, Cargo, Trigger, Status
  captain/          Captains, Dockhands, Conductors (jobs, linking), Voyage, Vessel, DockWork (carrying), CaptainBehavior, ConductorBehavior, DockhandBehavior, BoatPilot (sailing)
  mixin/            AbstractBoatMixin (vanilla only shows moving oars for a player at the helm; a golem captain's oars move too)
  rail/             Rails (the track as a graph), RailPilot (driving), Trains (finding, turning round)
  harbour/          HarbourRegistry (saved data), Harbours (wake-ups), Fleet (where captains are), Berths, Waters
  menu/             RouteMenu
  command/          HabourCommand
habour/src/client/  RouteScreen
habour/src/gametest/ the scripted client game test
```
