package de.elternstunden.auth.web;

import de.elternstunden.api.MeApi;
import de.elternstunden.api.model.MeDto;
import de.elternstunden.auth.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in account. */
@RestController
public class MeController implements MeApi {

    private final AuthService auth;

    public MeController(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public ResponseEntity<MeDto> getMe() {
        return ResponseEntity.ok(auth.me());
    }
}
