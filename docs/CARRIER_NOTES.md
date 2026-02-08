# Carrier-Specific Notes

## General MMS Troubleshooting

MMS transmission depends on carrier APN/mobile-data configuration and OEM telephony behavior. When delivery fails, API/message states report platform MMS error codes (e.g. invalid APN, network, HTTP, or retry-required).

Common steps that resolve most MMS issues:

1. Ensure mobile data is enabled for the SIM used to send messages
2. Reset APN settings to carrier defaults
3. Reboot the phone
4. Verify MMS/APN values with your carrier's support

## Phonero (Norway) on Pixel Devices

Phonero uses Telenor network infrastructure. If MMS delivery fails on Pixel devices:

1. Ensure mobile data is enabled for the SIM used to send messages
2. Reset APN settings to carrier defaults (Settings > Network > SIMs > Access Point Names > Reset to default)
3. Reboot the phone
4. Contact Phonero support to verify correct MMS/APN values for your plan

## Contributing Carrier Notes

If you've resolved MMS issues on a specific carrier/device combination, please open a PR to add your findings here.
