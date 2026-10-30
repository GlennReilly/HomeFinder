# Sales districts

The Valuer General publishes sales per council ("district"). Only these files are loaded. Newcastle (008) and Lake
Macquarie (004) are the pilot councils; Maitland, Port Stephens, Cessnock and Central Coast are included because several
pilot suburbs straddle their boundary (for example Thornton, Williamtown, Doyalson), so their sales would otherwise be
undercounted. A sale is counted only if its locality name and postcode match a pilot suburb, so a same-named suburb in a
neighbouring council cannot slip in. Drop the last four rows to use only the two pilot councils.

Codes come from the Valuer General's "Property Sales Data File (District Codes and Names)" fact sheet (May 2020); council
codes can change with amalgamations, so check it if a council's sales stop appearing.

`name_only_match` marks the pilot councils. A sale there with a blank postcode is matched by locality name alone when exactly one suburb has that name (ADR 0025). In the neighbouring councils a blank postcode stays unmatched, because the locality might be a same-named suburb outside the pilot area.
