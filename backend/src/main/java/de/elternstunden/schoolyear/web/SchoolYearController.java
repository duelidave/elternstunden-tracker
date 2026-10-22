package de.elternstunden.schoolyear.web;

import de.elternstunden.api.SchoolYearsApi;
import de.elternstunden.api.model.EnrollmentCopyResultDto;
import de.elternstunden.api.model.SchoolYearCreateDto;
import de.elternstunden.api.model.SchoolYearDto;
import de.elternstunden.api.model.SchoolYearUpdateDto;
import de.elternstunden.common.security.CurrentUser;
import de.elternstunden.schoolyear.domain.SchoolYear;
import de.elternstunden.schoolyear.service.SchoolYearService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** School years for admins (5.5, FS-ADM 7.7). */
@RestController
public class SchoolYearController implements SchoolYearsApi {

    private final SchoolYearService schoolYears;

    public SchoolYearController(SchoolYearService schoolYears) {
        this.schoolYears = schoolYears;
    }

    @Override
    public ResponseEntity<List<SchoolYearDto>> listSchoolYears() {
        Optional<UUID> current = schoolYears.current().map(SchoolYear::id);
        return ResponseEntity.ok(
                schoolYears.list().stream().map(year -> toDto(year, current)).toList());
    }

    @Override
    public ResponseEntity<SchoolYearDto> createSchoolYear(SchoolYearCreateDto body) {
        SchoolYear created = schoolYears.create(
                CurrentUser.principal().userId(),
                body.getLabel(),
                body.getStartsOn(),
                body.getEndsOn(),
                Objects.requireNonNullElse(body.getRequiredMinutes(), 600));
        return ResponseEntity.status(HttpStatus.CREATED).body(withCurrent(created));
    }

    @Override
    public ResponseEntity<SchoolYearDto> updateSchoolYear(UUID schoolYearId, SchoolYearUpdateDto body) {
        SchoolYear updated = schoolYears.update(
                CurrentUser.principal().userId(),
                schoolYearId,
                body.getLabel(),
                body.getStartsOn(),
                body.getEndsOn(),
                body.getRequiredMinutes());
        return ResponseEntity.ok(withCurrent(updated));
    }

    @Override
    public ResponseEntity<SchoolYearDto> closeSchoolYear(UUID schoolYearId) {
        return ResponseEntity.ok(
                withCurrent(schoolYears.close(CurrentUser.principal().userId(), schoolYearId)));
    }

    @Override
    public ResponseEntity<SchoolYearDto> reopenSchoolYear(UUID schoolYearId) {
        return ResponseEntity.ok(
                withCurrent(schoolYears.reopen(CurrentUser.principal().userId(), schoolYearId)));
    }

    @Override
    public ResponseEntity<EnrollmentCopyResultDto> copyPreviousEnrollments(UUID schoolYearId) {
        int copied = schoolYears.copyPreviousEnrollments(CurrentUser.principal().userId(), schoolYearId);
        return ResponseEntity.ok(new EnrollmentCopyResultDto(copied));
    }

    private SchoolYearDto withCurrent(SchoolYear year) {
        return toDto(year, schoolYears.current().map(SchoolYear::id));
    }

    private static SchoolYearDto toDto(SchoolYear year, Optional<UUID> current) {
        return new SchoolYearDto(
                year.id(),
                year.label(),
                year.startsOn(),
                year.endsOn(),
                year.requiredMinutes(),
                year.closed(),
                current.map(year.id()::equals).orElse(false));
    }
}
