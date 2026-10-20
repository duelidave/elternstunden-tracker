package de.elternstunden.common.web;

import de.elternstunden.api.SystemApi;
import de.elternstunden.api.model.StatusDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Public status page without details, e.g. for an external uptime check (OPS-07). */
@RestController
public class StatusController implements SystemApi {

    @Override
    public ResponseEntity<StatusDto> getStatus() {
        return ResponseEntity.ok(new StatusDto(StatusDto.StatusEnum.UP));
    }
}
