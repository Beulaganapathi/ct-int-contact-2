# CT INT CONTACT (Android)

Staff contact directory and cadre strength for the Office of the Joint Commissioner (ST), Chengalpattu (Intelligence) Division. Built from the Excel sheet, works fully offline.

## Screens

1. **Cadre Strength** (opens first): office header, "in position as on" date, Sanctioned / In position / Vacant, the STO / DSTO table, and unit-wise strength. Tap a unit to jump to its officers.
2. **Contacts**: all officers grouped by unit with search (name, post, unit or number). Tap the green phone icon to call. Tap a row for WhatsApp, SMS and Copy.

Calling opens the phone dialer with the number filled in, so no phone permission is needed.

## Build and run

1. Open the `CtIntContact` folder in Android Studio (Koala or newer) and let Gradle sync.
2. Run, or Build > Build APK(s) to get an APK to install on phones.

## PIN lock

The app asks for a PIN every time it opens (and again after 5 minutes in the background).

- **Open PIN** (give to everyone): view-only. They can read the strength page, search and call/WhatsApp/SMS.
- **Admin PIN** (only you): also Add / Edit / Delete, backup, restore, reset and Change PINs.

The built-in PINs are the two lines `OPEN_PIN_DEFAULT` and `ADMIN_PIN_DEFAULT` in `MainActivity.kt`. Change them before you share the app, then rebuild. After 5 wrong tries the app waits 30 seconds. "Change PINs" in the menu changes the PIN on that one phone only.

The PIN keeps casual users out; it does not make the numbers secret from someone who gets the APK file and decodes it. Share the APK only with the people who should have it.

## Cadres, sanctioned strength and officers

The first page is built from **cadres** (State Tax Officer, Deputy State Tax Officer, and any you add such as Assistant or Driver).

- Every row is a **post**. **Sanctioned** = number of posts, **Filled** = posts with an officer, **Vacant** = posts without one.
- Adding an officer: if the unit has a vacant post of that cadre, the officer fills it (only Filled goes up). Choose "Create a new post" to raise Sanctioned by 1.
- Delete: "Mark vacant" keeps the post; "Remove post" lowers Sanctioned.
- Admin: tap a cadre row to rename it or change its sanctioned number (more = new vacant posts in a unit you pick; fewer = vacant posts only). "+ Add cadre" adds a new cadre (e.g. Assistant, Driver).
- Admin: "Add officer" (bottom right, on both pages). Tap an officer in Contacts to Edit / Delete; tap a vacant post to Assign officer.
- The "in position as on" date on the first page always shows today's date on the phone.
- Menu (top right): share a backup of the list, restore from a copied backup, reset to the original list.

Edits are stored only on that phone. Uninstalling the app (needed when installing a new GitHub-built APK) erases them, so share a backup first and restore it after installing.

## Updating the original list from a new Excel sheet

All data comes from `app/src/main/assets/staff.json`, and the strength numbers are calculated from it (cadres with their sanctioned numbers, and the officers). To refresh from a new Excel sheet in the same format:

```
pip install openpyxl
python3 tools/xlsx_to_json.py "New Staff Sheet.xlsx"
```

Then rebuild the app.
