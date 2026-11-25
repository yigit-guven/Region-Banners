# Changelog

All notable changes to **Region Banners** will be documented in this file.

## [1.0.0] - 2026-11-25

### Added
- **Import Banner Icon** button on Xaero Regions' region edit screen when holding a Minecraft banner in the main hand or offhand.
- Banner-to-icon renderer converting the held banner's base dye color and all pattern layers into a crisp `12×12` map icon.
- Multiplayer syncing through Xaero Regions' custom icon pipeline so all players on the server see banner icons without needing Region Banners on the server or other clients.
- Deferred upload on **Save** and deterministic content-addressed banner UUID deduplication so identical or canceled icons never waste server storage.
- Client-side permission check before uploading custom icons.
