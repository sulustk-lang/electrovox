# Electrovox

Voxel-style wire rendering and anchoring for **Create: Electro Energetics** on
NeoForge 1.21.1.

Electro Energetics draws wires as stretched partial models. Electrovox keeps that
for ordinary wires and adds a second, opt-in look: wires built from small
axis-aligned cubes, with a compact white connector at each free-standing
attachment point.

## Features

- **Voxel wire geometry.** Wires are rasterised onto a 1/16 block grid (1/8 past
  the LOD distance) and drawn as snapped cubes. Only faces touching empty space
  are emitted, so the vertex cost stays low.
- **Per-wire opt-in.** A wire becomes voxel when it is placed while the voxel
  key is held; everything else keeps the addon's own Flywheel visual. The two
  renderers never draw the same wire.
- **Anchor connectors.** Clicking a wire with the voxel key splices it: the
  original connection is replaced by two connections meeting at a new node at the
  hit point, so the connector appears already wired in. Clicking a plain block
  creates an anchor there instead, which makes any surface a valid wire end.
  The click is taken from the use-key input, not from the block under the
  crosshair, so it works even though a voxel wire has no collision.
- **Path preview.** While a wire is being placed, the route is drawn ahead of the
  click, coloured by whether it can rest on geometry.
- **Wire colours.** The cube tint follows the wire type, including dyed insulated
  wires.
- **Survival balance.** Anchoring is charged the way the addon charges wires: one
  connector block per anchor and one spool per run, with the empty spool returned
  as usual. The run is also held to the wire type's maximum length. Creative and
  spectator pay nothing.
- **Contraption anchors.** An anchor placed on a Sable contraption is remembered
  by its plot position and recomputed from the contraption's pose every frame, so
  it stays glued to the hardware through translation and rotation with no
  visible lag. A wire whose two ends ride the same contraption is exempt from the
  addon's overload rule: the run is carried as a whole and can never snap, even
  while the contraption shakes.

## Requirements

| Component | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.219 or newer |
| Create | 6.0.7 – 6.1.0 |
| Create: Electro Energetics | 1.21.1-1.1.4 or newer |
| Sable | optional, 2.0.0 – 3.0.0 |
| Create: Aeronautics | optional, 1.3.0 or newer |

Sable and Create: Aeronautics are optional. Without them nothing changes. With
them, wires and anchors on sub-levels ride along with the contraption: the plot
position of a click is remembered and resolved against the contraption's pose,
so the cube pattern and the connector move with the hardware.

## Building

The third-party mod jars this project compiles against are not committed. Fetch
them once:

```bash
./fetch-libs.sh
./gradlew build
```

The jar lands in `build/libs/electrovox-<version>.jar`.

To run a development client:

```bash
./gradlew runClient
```

## Usage

1. Hold a wire spool.
2. Hold the voxel key (**left Ctrl** by default, rebindable under *Controls →
   Electrovox*).
3. Right-click a wire to splice a connector into it, or any block face to anchor
   a wire there.

The hotbar shows a `Voxel mode` badge while the key is held. In survival a second
line prices the click: the connector it will use, and for a run the wire type and
length. The line turns red when the run is longer than the type allows or the
inventory cannot pay.

## Configuration

`config/electrovox-client.toml`:

| Key | Default | Meaning |
| --- | --- | --- |
| `alwaysVoxel` | `false` | Draw every wire as voxels, ignoring the key |
| `wallRouting` | `false` | Pull the run onto nearby block faces instead of a straight line |
| `voxelLod` | `true` | Use a coarser grid far from the camera |
| `voxelLodDistance` | `32.0` | Distance in blocks where the coarse grid starts |
| `showModeIndicator` | `true` | Show the hotbar badge and price line |
| `survivalCosts` | `true` | Charge anchors and runs outside creative |

## How it fits together

| Piece | Role |
| --- | --- |
| `VoxelWirePass` | Draws voxel wires with Flywheel active, and retires their Flywheel effect only after a successful draw |
| `WireRendererMixin` | Swaps geometry when Flywheel is unavailable, and records which wires are voxel |
| `WallRouter` | Turns a straight run into a route that hugs block faces |
| `VoxelWireRenderer` | Rasterises a polyline into cubes and emits the visible faces |
| `VoxelAnchors` / `AnchorRenderer` | Derives anchors from detached nodes and draws them as small white squares |
| `AnchorPositions` | Client side: resolves a tracked anchor from the contraption's render pose, so it never lags behind |
| `AnchorTracking` | Server side: stores each anchor's plot position, moves it with its contraption, and repositions the wires |
| `WireSimulationStateMixin` | Pays a contraption wire out to its span and forbids the addon from snapping it |
| `InWorldNodeDataAccessor` | Lets the server write a detached node's world position |
| `BlockAnchorHook` | Client side: hit-tests the look ray against voxel wires and prices the click |
| `VoxelBalance` | Shared cost rules: one connector per anchor, one spool per run |
| `SableSupport` | Projects a click out of a sub-level; a no-op when Sable is absent |
| `TapWireHandler` / `PlaceVoxelNodeHandler` | Server side: splice a tapped wire, or anchor on any block, charging the cost |

## License

MIT. See [LICENSE](LICENSE).

Create, Create: Electro Energetics, Flywheel, Ponder and Registrate are their
authors' work and are not distributed here.
