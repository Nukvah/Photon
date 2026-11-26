# Google Play listing

Text and answers for the Play Console, ready to paste. Images are in this folder
(`icon-512.png`, `feature-graphic.png`); phone screenshots are built by CI from
`StoreScreenshotTest` (artifact of the CI run, folder `src/test/screenshots/store/`).

## Main store listing

**App name** (max 30): `Photon: Small photos for email`

**Short description** (max 80):

> Make photos small enough for any email. Private, offline, no ads, no tracking.

**Full description** (max 4000):

> Phone photos are often 4 to 10 MB each. Attach a few and the email bounces.
> Photon makes them small enough to send, in seconds, without uploading anything.
>
> HOW IT WORKS
> • In your gallery, select photos and tap Share, then Photon. Or open Photon and tap Select photos.
> • Photon shrinks them right away.
> • Tap Share via email and they land in a new message in your email app. Or save them to a folder, or send them to any other app.
>
> THREE SIZES
> • Small: about 80 KB per photo. Best for 20 or more photos.
> • Standard: about 150 KB per photo. Sharp full screen on a laptop.
> • Sharp: about 250 KB per photo. For photos people may print or zoom.
>
> PRIVATE BY DESIGN
> • Everything happens on your phone. Photon has no internet permission, so your photos cannot leave it unless you send them.
> • No account, no ads, no analytics, no tracking.
> • Location, camera details and other metadata are removed from the copies.
> • Your originals are never changed.
>
> GOOD QUALITY, SMALL FILES
> Photon uses jpegli, a modern JPEG encoder, so photos stay sharp at a fraction of the size.
> It reads JPEG, PNG, WebP, HEIC and AVIF, fixes rotation and colours, and warns you when a
> batch is still over 10 MB, the limit many inboxes have.
>
> FREE AND OPEN SOURCE
> Photon is free software under the GNU GPL v3. The source code is at https://github.com/Nukvah/Photon

**App category**: Photography
**Tags**: pick photo-editing / photo-compression tags offered by the Console.
**Contact email**: required (shown publicly). Use a project address, not a personal one.
**Website**: https://github.com/Nukvah/Photon
**Privacy policy URL**: https://github.com/Nukvah/Photon/blob/main/PRIVACY.md

## App content answers

| Section | Answer |
| --- | --- |
| Privacy policy | URL above |
| Ads | No, the app does not contain ads |
| App access | All functionality is available without special access |
| Content rating (IARC) | Category "All other app types"; answer No to every question (no violence, sex, language, drugs, gambling, user interaction, sharing of location, purchases). Expect Everyone / PEGI 3 |
| Target audience | 18 and over (avoids the Families policy; the app is still fine for anyone) |
| News app | No |
| Data safety | Collects no data, shares no data (see below) |
| Government app | No |
| Financial features | None |
| Health | None |

### Data safety form

- Does your app collect or share any of the required user data types? **No.**
  Photos are processed only on the device and leave it only when the user shares them
  through Android's share sheet, which Google does not count as collection.
- Is all of the user data collected by your app encrypted in transit? Not asked once you answer No.
- Do you provide a way for users to request that their data is deleted? Not asked once you answer No.

## Policy notes

- The Play build has no Donate row (Play does not allow links to outside payment pages),
  so do not mention donations or Ko-fi in the listing either.
- With no paid features or in-app payments, the account can be declared a **non-trader**
  under the EU Digital Services Act.
