# Hoistway

Working elevators and escalators for Minecraft 1.21.1 (NeoForge).

The elevator is a real moving car, not a teleporter: it hangs from a winch on two cables,
accelerates and brakes, carries everything standing on it, and stops at the floors you mark
with call buttons.

## Building an elevator

1. **Dig a shaft.** 1×1 for the Elevator Car, 3×3 for the Large Elevator Car.
2. **Place an Elevator Motor** at the top, centred over the shaft, at least three blocks above
   the highest floor.
3. **Load cable.** Open the motor and put Hoist Cable in it. Each cable lets the car travel one
   block further down, so a 40 block shaft needs 40 cable.
4. **Place the car.** Use the car item on the floor of the shaft, directly under the motor.
5. **Mark the stops.** Put a Call Button on a wall within three blocks of the shaft on every
   floor, at foot or eye level. Each floor with a button is a stop.

### Riding

| Action | Result |
| --- | --- |
| Press a Call Button | Calls the car to that floor. The button stays lit until it arrives. |
| Jump while standing in the car | Go up one stop. Press again while moving to go one further. |
| Sneak while standing in the car | Go down one stop. |
| Punch the car while it is parked | Picks it back up. |

The car waits if something is underneath it and stops if the shaft is blocked.

### Upgrades

The motor has five slots, shared between cable and upgrades.

| Item | Effect |
| --- | --- |
| Hoist Cable | +1 block of travel each (up to 64 per slot) |
| Speed Upgrade | +1.6 blocks per second each (base speed is 2.4) |
| Stop Upgrade | +2 stops each (base is 3 stops) |

If there are more buttoned floors than the motor has stops, the floors nearest the motor are
served.

## Escalators

Place Escalator blocks in a diagonal run, the way you would place stairs; they climb in the
direction you are facing. The steps move and carry players, mobs and items along.
Right-click a run with an empty hand to reverse it.

## Recipes

| Result | Pattern (rows top to bottom) | Ingredients |
| --- | --- | --- |
| 16 Hoist Cable | `I` / `I` / `I` | I iron ingot |
| Elevator Motor | `IRI` / `CPC` / `IBI` | I iron ingot, R redstone block, C hoist cable, P piston, B iron block |
| Elevator Car | `ICI` / `I I` / `PPP` | I iron ingot, C hoist cable, P heavy weighted pressure plate |
| Large Elevator Car | `IPI` / `PEP` / `IPI` | I iron ingot, P heavy weighted pressure plate, E elevator car |
| 3 Call Buttons | `I` / `B` / `R` | I iron ingot, B stone button, R redstone |
| Speed Upgrade | `GRG` / `RSR` / `GRG` | G gold ingot, R redstone, S sugar |
| Stop Upgrade | `GRG` / `RLR` / `GRG` | G gold ingot, R redstone, L lapis lazuli |
| 4 Escalators | `I  ` / `SI ` / `RSI` | I iron ingot, S smooth stone, R redstone |

## Building from source

Requires Java 21.

```
./gradlew build
```

The jar is written to `build/libs`.

Textures are drawn as pixel grids in `tools/make_textures.py`, and the models, recipes and
other JSON come from `tools/make_data.py`. Run either script with Python 3 after editing it.

## License

MIT. See [LICENSE](LICENSE).
