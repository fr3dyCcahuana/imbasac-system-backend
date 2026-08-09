package com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.proformav2.domain.model.VoidProformaV2Response;
import com.paulfernandosr.possystembackend.proformav2.domain.port.input.CreateProformaV2UseCase;
import com.paulfernandosr.possystembackend.proformav2.domain.port.input.ConvertProformaToSaleV2UseCase;
import com.paulfernandosr.possystembackend.proformav2.domain.port.input.GetProformaV2UseCase;
import com.paulfernandosr.possystembackend.proformav2.domain.port.input.GetProformasV2PageUseCase;
import com.paulfernandosr.possystembackend.proformav2.domain.port.input.UpdateProformaV2UseCase;
import com.paulfernandosr.possystembackend.proformav2.domain.port.input.VoidProformaV2UseCase;
import com.paulfernandosr.possystembackend.stockreservation.domain.port.input.ExpireStockReservationsUseCase;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.CreateProformaV2Request;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ConvertProformaV2Request;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ConvertProformaV2Response;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaV2Response;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.VoidProformaV2Request;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.PageResponse;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaV2SummaryResponse;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.UpdateProformaV2Request;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaCreatorResponse;
import com.paulfernandosr.possystembackend.proformav2.infrastructure.adapter.input.dto.ProformaCreatorRoleResponse;
import com.paulfernandosr.possystembackend.role.domain.RoleName;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/proformas/v2")
@RequiredArgsConstructor
public class ProformaV2Controller {

    private final CreateProformaV2UseCase createUseCase;
    private final GetProformaV2UseCase getUseCase;
    private final ConvertProformaToSaleV2UseCase convertUseCase;
    private final VoidProformaV2UseCase voidUseCase;
    private final GetProformasV2PageUseCase getProformasV2PageUseCase;
    private final UpdateProformaV2UseCase updateUseCase;
    private final ExpireStockReservationsUseCase expireStockReservationsUseCase;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<SuccessResponse<ProformaV2Response>> create(
            @RequestBody CreateProformaV2Request request,
            Principal principal
    ) {
        String username = principal != null ? principal.getName() : null;
        ProformaV2Response created = createUseCase.create(request, username);
        return ResponseEntity
                .created(URI.create("/proformas/v2/" + created.getNumber()))
                .body(SuccessResponse.ok(created));
    }

    @GetMapping("/{number}")
    public ResponseEntity<SuccessResponse<ProformaV2Response>> get(
            @PathVariable("number") Long number,
            Principal principal
    ) {
        User currentUser = resolveCurrentUser(principal);
        ProformaV2Response response = getUseCase.getByNumber(number);
        ensureClientOwnsProforma(currentUser, response);
        return ResponseEntity.ok(SuccessResponse.ok(response));
    }

    @PostMapping("/stock-reservations/expire")
    public ResponseEntity<SuccessResponse<Integer>> expireStockReservations() {
        return ResponseEntity.ok(SuccessResponse.ok(expireStockReservationsUseCase.expirePreviousDays()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SuccessResponse<ProformaV2Response>> update(
            @PathVariable("id") Long id,
            @RequestBody UpdateProformaV2Request request,
            Principal principal
    ) {
        String username = principal != null ? principal.getName() : null;
        rejectClientMutation(principal);
        return ResponseEntity.ok(SuccessResponse.ok(updateUseCase.update(id, request, username)));
    }

    @PostMapping("/{number}/convert")
    public ResponseEntity<SuccessResponse<ConvertProformaV2Response>> convert(
            @PathVariable("number") Long number,
            @RequestBody ConvertProformaV2Request request,
            Principal principal
    ) {
        String username = principal != null ? principal.getName() : null;
        // Regla de negocio: el facturador convierte por NÚMERO visible de proforma.
        // El service resuelve internamente p.id para guardar la relación por ID real.
        rejectClientMutation(principal);
        ConvertProformaV2Response response = convertUseCase.convert(number, request, username);
        return ResponseEntity.ok(SuccessResponse.ok(response));
    }

    /**
     * Endpoint principal para anular una proforma.
     * Solo permite anular proformas en estado PENDIENTE.
     */
    @PostMapping("/{id}/anular")
    public ResponseEntity<SuccessResponse<VoidProformaV2Response>> anularProforma(
            @PathVariable("id") Long id,
            @RequestBody(required = false) VoidProformaV2Request request,
            Principal principal
    ) {
        rejectClientMutation(principal);
        String reason = request == null ? null : request.getReason();
        return ResponseEntity.ok(SuccessResponse.ok(voidUseCase.voidProforma(id, reason)));
    }

    /**
     * Alias técnico mantenido por compatibilidad.
     * Se recomienda que el frontend use POST /proformas/v2/{id}/anular.
     */
    @PostMapping("/{id}/void")
    public ResponseEntity<SuccessResponse<VoidProformaV2Response>> voidProforma(
            @PathVariable("id") Long id,
            @RequestBody(required = false) VoidProformaV2Request request,
            Principal principal
    ) {
        rejectClientMutation(principal);
        String reason = request == null ? null : request.getReason();
        return ResponseEntity.ok(SuccessResponse.ok(voidUseCase.voidProforma(id, reason)));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<PageResponse<ProformaV2SummaryResponse>>> findPage(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long createdBy,
            @RequestParam(required = false) Long createdByRoleId,
            @RequestParam(required = false) Boolean edited,
            @RequestParam(required = false) String paymentType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal
    ) {
        User currentUser = resolveCurrentUser(principal);
        Long scopedCreatedBy = isClient(currentUser) ? currentUser.getId() : createdBy;
        Long scopedCreatedByRoleId = isClient(currentUser) ? null : createdByRoleId;

        return ResponseEntity.ok(SuccessResponse.ok(
                getProformasV2PageUseCase.findPage(
                        status,
                        query,
                        scopedCreatedBy,
                        scopedCreatedByRoleId,
                        edited,
                        paymentType,
                        dateFrom,
                        dateTo,
                        page,
                        size
                )
        ));
    }

    @GetMapping("/creators")
    public ResponseEntity<SuccessResponse<List<ProformaCreatorResponse>>> findCreators(Principal principal) {
        User currentUser = resolveCurrentUser(principal);
        if (isClient(currentUser)) {
            return ResponseEntity.ok(SuccessResponse.ok(List.of(
                    ProformaCreatorResponse.builder()
                            .id(currentUser.getId())
                            .name(userDisplayName(currentUser))
                            .build()
            )));
        }

        return ResponseEntity.ok(SuccessResponse.ok(getProformasV2PageUseCase.findCreators()));
    }

    @GetMapping("/creator-roles")
    public ResponseEntity<SuccessResponse<List<ProformaCreatorRoleResponse>>> findCreatorRoles(Principal principal) {
        if (isClient(resolveCurrentUser(principal))) {
            return ResponseEntity.ok(SuccessResponse.ok(List.of()));
        }

        return ResponseEntity.ok(SuccessResponse.ok(getProformasV2PageUseCase.findCreatorRoles()));
    }

    private User resolveCurrentUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }

        return userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado"));
    }

    private boolean isClient(User user) {
        return user != null
                && user.getRole() != null
                && RoleName.CLIENTE.equals(user.getRole().getName());
    }

    private void ensureClientOwnsProforma(User currentUser, ProformaV2Response response) {
        if (!isClient(currentUser)) {
            return;
        }

        if (response == null || response.getCreatedBy() == null || !response.getCreatedBy().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a esta proforma");
        }
    }

    private void rejectClientMutation(Principal principal) {
        if (isClient(resolveCurrentUser(principal))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El rol cliente solo puede consultar y previsualizar proformas");
        }
    }

    private String userDisplayName(User user) {
        String fullName = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return fullName.isBlank() ? user.getUsername() : fullName;
    }
}
