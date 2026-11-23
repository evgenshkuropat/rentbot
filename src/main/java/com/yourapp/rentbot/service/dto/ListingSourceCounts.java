package com.yourapp.rentbot.service.dto;

import com.yourapp.rentbot.service.OwnerSignalDetector;
import java.util.Collection;
import java.util.Locale;

/** Each listing belongs to one source; possibleOwner is an overlapping text signal. */
public record ListingSourceCounts(int sreality, int idnes, int bezrealitky, int bazos,
                                  int digireality, int realfree, int verifiedOwner, int other,
                                  int possibleOwner) {
    public static ListingSourceCounts empty() {
        return new ListingSourceCounts(0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public static ListingSourceCounts from(Collection<ListingDto> listings) {
        int sreality = 0, idnes = 0, bezrealitky = 0, bazos = 0;
        int digireality = 0, realfree = 0, verifiedOwner = 0, other = 0, possibleOwner = 0;
        for (ListingDto listing : listings) {
            if (listing == null) continue;
            String source = OwnerSignalDetector.visibleSource(listing.source()).trim().toLowerCase(Locale.ROOT);
            if (OwnerSignalDetector.isVerifiedOwnerListing(listing)) verifiedOwner++;
            else if (source.equals("sreality")) sreality++;
            else if (source.equals("idnes")) idnes++;
            else if (source.equals("bezrealitky")) bezrealitky++;
            else if (source.equals("bazoš") || source.equals("bazos")) bazos++;
            else if (source.equals("digireality") || source.equals("digireality owner")) digireality++;
            else if (source.equals("realfree")) realfree++;
            else other++;
            if (!OwnerSignalDetector.isVerifiedOwnerListing(listing)
                    && OwnerSignalDetector.isPossibleOwnerListing(listing)) possibleOwner++;
        }
        return new ListingSourceCounts(sreality, idnes, bezrealitky, bazos, digireality,
                realfree, verifiedOwner, other, possibleOwner);
    }

    public int total() {
        return sreality + idnes + bezrealitky + bazos + digireality + realfree + verifiedOwner + other;
    }

    public ListingSourceCounts plus(ListingSourceCounts counts) {
        return new ListingSourceCounts(sreality + counts.sreality, idnes + counts.idnes,
                bezrealitky + counts.bezrealitky, bazos + counts.bazos, digireality + counts.digireality,
                realfree + counts.realfree, verifiedOwner + counts.verifiedOwner, other + counts.other,
                possibleOwner + counts.possibleOwner);
    }
}
