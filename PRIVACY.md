# Privacy

Zork I runs entirely on-device. It makes no network requests, collects no data, and has no analytics or accounts. The story file is bundled in the APK.

## Permissions

The tool declares its permissions in `tool/lighttool.toml`. The merged APK also inherits Android
permissions and components from the current Light SDK dependency graph.

## Analytics and accounts

None. There is no analytics integration, no advertising, no tracking, and no account. Nothing is
collected or shared by us.

## Contact

Open an issue on the project's GitHub repository.

## Content licensing

Zork I source code and the Z-Machine specification are used under the MIT License
(Microsoft). The interpreter engine `de.onyxbits.textfiction.zengine` is Apache-2.0;
the original Zplet interpreter by Matthew T. Russotto is under the Artistic License.
No network access; the game runs entirely on-device.
