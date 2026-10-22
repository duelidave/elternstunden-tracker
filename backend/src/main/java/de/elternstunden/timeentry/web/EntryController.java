package de.elternstunden.timeentry.web;

import de.elternstunden.api.EntriesApi;
import de.elternstunden.api.model.AdminEntryDto;
import de.elternstunden.api.model.ConfirmationResultDto;
import de.elternstunden.api.model.EntryConfirmationDto;
import de.elternstunden.api.model.EntryCreateDto;
import de.elternstunden.api.model.EntryPageDto;
import de.elternstunden.api.model.EntryStatusDto;
import de.elternstunden.api.model.EntryUpdateDto;
import de.elternstunden.common.security.CurrentUser;
import de.elternstunden.common.web.Paging;
import de.elternstunden.family.service.FamilyService;
import de.elternstunden.timeentry.domain.EntryStatus;
import de.elternstunden.timeentry.domain.EntryView;
import de.elternstunden.timeentry.service.EntryService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Confirmation and corrections by admins (FS-CONF 7.5). */
@RestController
public class EntryController implements EntriesApi {

    private final EntryService entries;
    private final FamilyService families;

    public EntryController(EntryService entries, FamilyService families) {
        this.entries = entries;
        this.families = families;
    }

    @Override
    public ResponseEntity<EntryPageDto> listEntries(
            @Nullable EntryStatusDto status,
            @Nullable UUID familyId,
            @Nullable LocalDate from,
            @Nullable LocalDate to,
            Integer page,
            Integer size) {
        Paging paging = Paging.of(page, size);
        List<EntryView> all =
                entries.search(status == null ? null : EntryStatus.valueOf(status.getValue()), familyId, from, to);
        Map<UUID, String> labels = families.displayLabels();
        return ResponseEntity.ok(new EntryPageDto(
                page,
                size,
                (long) all.size(),
                paging.totalPages(all.size()),
                paging.slice(all).stream()
                        .map(view -> EntryMapper.admin(
                                view, labels.getOrDefault(view.entry().familyId(), "")))
                        .toList()));
    }

    @Override
    public ResponseEntity<ConfirmationResultDto> confirmEntries(EntryConfirmationDto body) {
        EntryService.Confirmation result =
                entries.confirm(CurrentUser.principal().userId(), body.getIds());
        return ResponseEntity.ok(new ConfirmationResultDto(result.confirmed(), result.skipped()));
    }

    @Override
    public ResponseEntity<AdminEntryDto> confirmEntry(UUID entryId) {
        return ResponseEntity.ok(admin(entries.confirm(CurrentUser.principal().userId(), entryId)));
    }

    @Override
    public ResponseEntity<AdminEntryDto> createEntryForFamily(UUID familyId, EntryCreateDto body) {
        EntryView created =
                entries.createForFamily(CurrentUser.principal().userId(), familyId, EntryMapper.content(body));
        return ResponseEntity.status(HttpStatus.CREATED).body(admin(created));
    }

    @Override
    public ResponseEntity<AdminEntryDto> updateEntry(UUID entryId, EntryUpdateDto body) {
        return ResponseEntity.ok(admin(entries.update(
                CurrentUser.principal().userId(), entryId, body.getVersion(), EntryMapper.change(body))));
    }

    @Override
    public ResponseEntity<Void> deleteEntry(UUID entryId) {
        entries.delete(CurrentUser.principal().userId(), entryId);
        return ResponseEntity.noContent().build();
    }

    private AdminEntryDto admin(EntryView view) {
        return EntryMapper.admin(view, families.displayLabel(view.entry().familyId()));
    }
}
