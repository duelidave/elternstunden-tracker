package de.elternstunden.report.web;

import de.elternstunden.api.ReportsApi;
import de.elternstunden.api.model.ReportKindDto;
import de.elternstunden.api.model.ReportScopeDto;
import de.elternstunden.common.security.CurrentUser;
import de.elternstunden.report.domain.ReportKind;
import de.elternstunden.report.domain.ReportScope;
import de.elternstunden.report.service.BillingReportService;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Billing PDF as a download (FS-PDF 7.8); generated on demand, never cached. */
@RestController
public class ReportController implements ReportsApi {

    private final BillingReportService reports;

    public ReportController(BillingReportService reports) {
        this.reports = reports;
    }

    @Override
    public ResponseEntity<Resource> getBillingReport(
            ReportScopeDto scope, @Nullable UUID schoolYearId, @Nullable UUID familyId, @Nullable ReportKindDto kind) {
        BillingReportService.Report report = reports.billing(
                CurrentUser.principal().userId(),
                schoolYearId,
                ReportScope.valueOf(scope.getValue()),
                familyId,
                kind == null ? null : ReportKind.valueOf(kind.getValue()));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(report.fileName())
                                .build()
                                .toString())
                .body(new ByteArrayResource(report.pdf()));
    }
}
