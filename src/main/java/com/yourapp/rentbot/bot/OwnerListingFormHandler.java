package com.yourapp.rentbot.bot;

import com.yourapp.rentbot.domain.OwnerListing;
import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.i18n.Language;
import com.yourapp.rentbot.service.OwnerListingInputParser;
import com.yourapp.rentbot.service.OwnerListingMessages;
import com.yourapp.rentbot.service.OwnerListingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Owns the temporary owner-listing form and its state transitions. */
@Component
class OwnerListingFormHandler {
    private static final Logger log = LoggerFactory.getLogger(OwnerListingFormHandler.class);

    enum ReplyKeyboard { PERSISTENT, CONFIRM }

    record Result(String text, ReplyKeyboard keyboard, OwnerListing submittedListing) {
        static Result persistent(String text) { return new Result(text, ReplyKeyboard.PERSISTENT, null); }
        static Result confirm(String text) { return new Result(text, ReplyKeyboard.CONFIRM, null); }
        static Result submitted(String text, OwnerListing listing) { return new Result(text, ReplyKeyboard.PERSISTENT, listing); }
    }

    private final Map<Long, OwnerListingDraft> drafts = new ConcurrentHashMap<>();
    private final OwnerListingInputParser inputParser;
    private final OwnerListingMessages messages;
    private final OwnerListingService ownerListings;

    OwnerListingFormHandler(OwnerListingInputParser inputParser,
                            OwnerListingMessages messages,
                            OwnerListingService ownerListings) {
        this.inputParser = inputParser;
        this.messages = messages;
        this.ownerListings = ownerListings;
    }

    Result start(long userId, String username, Language language) {
        OwnerListingDraft draft = new OwnerListingDraft();
        draft.createdByUsername = username;
        drafts.put(userId, draft);
        return Result.persistent(messages.start(language));
    }

    boolean hasDraft(long userId) {
        return drafts.containsKey(userId);
    }

    void discard(long userId) {
        drafts.remove(userId);
    }

    Result cancel(long userId, Language language) {
        discard(userId);
        return Result.persistent(messages.cancelled(language));
    }

    Result handleText(long userId, String text, Language language) {
        OwnerListingDraft draft = drafts.get(userId);
        if (draft == null) {
            return Result.persistent(messages.draftNotReady(language));
        }

        return switch (draft.step) {
            case CITY -> handleCity(draft, text, language);
            case LOCALITY -> handleLocality(draft, text, language);
            case LAYOUT -> handleLayout(draft, text, language);
            case PRICE -> handlePrice(draft, text, language);
            case TITLE -> handleTitle(draft, text, language);
            case DESCRIPTION -> handleDescription(draft, text, language);
            case CONTACT -> handleContact(draft, text, language);
            case PHOTO -> Result.persistent(messages.photoRequired(language));
            case CONFIRM -> handleConfirmation(userId, draft, text, language);
        };
    }

    Result handlePhoto(long userId, String photoFileId, Language language) {
        OwnerListingDraft draft = drafts.get(userId);
        if (draft == null) {
            return null;
        }
        if (draft.step != OwnerListingDraft.Step.PHOTO) {
            return Result.persistent(messages.unexpectedPhoto(language, draft.stepLabel(language)));
        }
        if (photoFileId == null || photoFileId.isBlank()) {
            return Result.persistent(messages.photoRequired(language));
        }
        draft.photoFileId = photoFileId;
        draft.step = OwnerListingDraft.Step.CONFIRM;
        return Result.confirm(preview(draft, language));
    }

    Result submit(long userId, Language language) {
        OwnerListingDraft draft = drafts.get(userId);
        if (draft == null || !draft.readyToPublish()) {
            return Result.persistent(messages.draftNotReady(language));
        }
        try {
            OwnerListing saved = ownerListings.savePending(toListing(userId, draft));
            drafts.remove(userId);
            return Result.submitted(messages.submitted(language), saved);
        } catch (Exception e) {
            log.error("Owner listing save failed for user={}", userId, e);
            return Result.confirm(messages.submitFailed(language));
        }
    }

    private Result handleCity(OwnerListingDraft draft, String text, Language language) {
        Optional<Region> region = inputParser.findRegion(text);
        if (region.isEmpty()) return Result.persistent(messages.regionNotFound(language));
        draft.region = region.get();
        draft.step = OwnerListingDraft.Step.LOCALITY;
        return Result.persistent(messages.localityPrompt(language));
    }

    private Result handleLocality(OwnerListingDraft draft, String text, Language language) {
        draft.locality = inputParser.required(text);
        if (draft.locality == null) return Result.persistent(messages.localityRequired(language));
        draft.step = OwnerListingDraft.Step.LAYOUT;
        return Result.persistent(messages.layoutPrompt(language));
    }

    private Result handleLayout(OwnerListingDraft draft, String text, Language language) {
        String layout = inputParser.layout(text);
        if (layout == null) return Result.persistent(messages.layoutInvalid(language));
        draft.layout = layout;
        draft.step = OwnerListingDraft.Step.PRICE;
        return Result.persistent(messages.pricePrompt(language));
    }

    private Result handlePrice(OwnerListingDraft draft, String text, Language language) {
        Integer price = inputParser.price(text);
        if (price == null) return Result.persistent(messages.priceInvalid(language));
        draft.priceCzk = price;
        draft.step = OwnerListingDraft.Step.TITLE;
        return Result.persistent(messages.titlePrompt(language));
    }

    private Result handleTitle(OwnerListingDraft draft, String text, Language language) {
        draft.title = inputParser.required(text);
        if (draft.title == null) return Result.persistent(messages.titleRequired(language));
        draft.step = OwnerListingDraft.Step.DESCRIPTION;
        return Result.persistent(messages.descriptionPrompt(language));
    }

    private Result handleDescription(OwnerListingDraft draft, String text, Language language) {
        draft.description = "-".equals(text.trim()) ? null : text.trim();
        draft.step = OwnerListingDraft.Step.CONTACT;
        return Result.persistent(messages.contactPrompt(language));
    }

    private Result handleContact(OwnerListingDraft draft, String text, Language language) {
        draft.contact = inputParser.required(text);
        if (draft.contact == null) return Result.persistent(messages.contactRequired(language));
        draft.step = OwnerListingDraft.Step.PHOTO;
        return Result.persistent(messages.photoRequired(language));
    }

    private Result handleConfirmation(long userId, OwnerListingDraft draft, String text, Language language) {
        if (inputParser.isSubmit(text)) return submit(userId, language);
        if (inputParser.isCancel(text)) return cancel(userId, language);
        return Result.confirm(messages.confirmHelp(language));
    }

    private OwnerListing toListing(long userId, OwnerListingDraft draft) {
        OwnerListing listing = new OwnerListing();
        listing.setCreatedByTelegramId(userId);
        listing.setCreatedByUsername(draft.createdByUsername);
        listing.setRegion(draft.region);
        listing.setLocality(draft.locality);
        listing.setLayout(draft.layout);
        listing.setPriceCzk(draft.priceCzk);
        listing.setTitle(draft.title);
        listing.setDescription(draft.description);
        listing.setContact(draft.contact);
        listing.setPhotoFileId(draft.photoFileId);
        listing.setCreatedAt(Instant.now());
        return listing;
    }

    private String preview(OwnerListingDraft draft, Language language) {
        return messages.preview(
                language,
                draft.region == null ? "—" : draft.region.getTitle(),
                value(draft.locality), value(draft.layout), price(draft.priceCzk), value(draft.title),
                value(draft.description), value(draft.contact), draft.photoFileId != null);
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private String price(Integer price) {
        return price == null || price <= 0 ? "—" : String.format("%,d", price).replace(',', ' ') + " Kč";
    }
}
