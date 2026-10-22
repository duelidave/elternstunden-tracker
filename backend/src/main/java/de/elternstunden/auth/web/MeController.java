package de.elternstunden.auth.web;

import de.elternstunden.api.MeApi;
import de.elternstunden.api.model.ChildClaimDto;
import de.elternstunden.api.model.MeDto;
import de.elternstunden.auth.service.MeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in account and, for unassigned family members, their child data (7.1.7). */
@RestController
public class MeController implements MeApi {

    private final MeService me;

    public MeController(MeService me) {
        this.me = me;
    }

    @Override
    public ResponseEntity<MeDto> getMe() {
        return ResponseEntity.ok(me.current());
    }

    @Override
    public ResponseEntity<MeDto> updateMyClaim(ChildClaimDto childClaimDto) {
        return ResponseEntity.ok(me.updateClaim(childClaimDto));
    }
}
