# Art sources

Every image LowTalk ships is kept here in the form it was made in, so anybody can see where it came from. No
generative AI is used for any image in this mod; the rule is in [CONTRIBUTING.md](../../CONTRIBUTING.md) and the
reasoning is in the AI Use Disclosure in the [README](../../README.md).

## Tool icon

A speech bubble being clicked: the icon of the LowTalk tool item, in the hotbar and the creative menu.

Provided by [@Trix8ea](https://x.com/Trix8ea) on X in September 2026 for use in LowTalk. The credit is in the
README and in the CurseForge listing. What is kept here is the working file the artist drew in, not a flattened
export, so the construction of the image can be inspected.

| File | What it is |
| --- | --- |
| `tool-icon.psd` | The layered Photoshop source. 64x64, 8-bit RGB with alpha, 10 layers, including named vector shape layers (`Shape 1`, `Ellipse 1`, `Ellipse 2` and two copies of it) and a layer group. This is the file to edit. |
| `tool-icon.png` | The cream export. This is the icon the mod ships. |
| `tool-icon-blue.png` | A second colourway from the same artwork, kept for reference. The mod does not use it. |

The shipped copy at `src/main/resources/Common/Icons/Items/LowTalk/Tool.png` is byte for byte the export above,
so the file a player's client downloads is the artist's file, unchanged:

```
eb8727e87128bdca158a30f4012f1b8e5a8096153fd2130602e6d3f08593f3fc  tool-icon.png  (and the shipped copy)
9d5eed6bd706ca0b90e91868c3bae2a3d15538d28b093ac5a302a2a3f5c8ec01  tool-icon-blue.png
3c77995e38ebad59204a51ef10b764066feaefced6354ec279b6027032cef34e  tool-icon.psd
```

Hytale's client names cached assets by their sha256, so the first hash is also what appears in a client log line
for `Icons/Items/LowTalk/Tool.png`.

Item icons must live under `Icons/ItemsGenerated/` or `Icons/Items/`; anywhere else and the server rejects the
whole item asset at load, and the item shows as a question mark in the hotbar.
