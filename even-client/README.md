# V-pet Stream (Even G2)

Companion LCD/gamepad for V-pet Glyph preview11+. The phone runs emulation; the EHPK streams a monochrome LCD and returns pulse inputs. No firmware bundled.

Requires Even App2.2.10+ and SDK0.0.16. Build: `npm ci && npm test && npm run build && npm run pack`. Package output: `vpet-stream.ehpk`.

Enable streaming in the phone app's **Even G2 & save sync** menu, start your pet, copy the pairing key here, connect phone, then connect glasses. The phone and Even App must be on the same Android device. The host binds only `127.0.0.1:8766`.

Classic gestures: press A, swipe up B, swipe down C, double B. D-Power: press Up, swipe up Left, swipe down Right, double Down. D-Power also exposes Step and Card input through the native glasses contextual menu (tap then long press); browser controls include both actions. Remote commands are pulses; no held inputs survive a disconnect.

This is a local development package: real G2 transport, Android lock/reconnect behavior, and localhost HTTP permissions still need device validation. Even's production networking guidance requires HTTPS; a desktop browser or simulator cannot establish production compatibility.

See [protocol, setup and validation](../docs/even-and-save-exchange.md).

Display lifecycle and BMP encoder adapted from the local DigiviceEven project. Teko and Share Tech Mono fonts are bundled under the SIL Open Font License; license texts are in `public/fonts/`. Even SDK uses its package license.
