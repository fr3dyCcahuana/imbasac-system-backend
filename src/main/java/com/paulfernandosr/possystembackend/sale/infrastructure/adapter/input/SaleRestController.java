package com.paulfernandosr.possystembackend.sale.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.domain.Page;
import com.paulfernandosr.possystembackend.common.domain.Pageable;
import com.paulfernandosr.possystembackend.common.infrastructure.mapper.PageMapper;
import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.sale.application.query.GetFullSaleSessionInfoQuery;
import com.paulfernandosr.possystembackend.sale.domain.Sale;
import com.paulfernandosr.possystembackend.sale.domain.SaleDocument;
import com.paulfernandosr.possystembackend.sale.domain.SaleSession;
import com.paulfernandosr.possystembackend.sale.domain.port.input.*;
import com.paulfernandosr.possystembackend.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Collection;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sales")
public class SaleRestController {
    private final CreateNewSaleUseCase createNewSaleUseCase;
    private final GetFullSaleInfoUseCase getFullSaleInfoUseCase;
    private final GetPageOfSalesUseCase getPageOfSalesUseCase;
    private final GetPageOfSaleSessionsUseCase getPageOfSaleSessionsUseCase;
    private final OpenSaleSessionUseCase openSaleSessionUseCase;
    private final CloseSaleSessionUseCase closeSaleSessionUseCase;
    private final GetFullSaleSessionInfoUseCase getFullSaleSessionInfoUseCase;
    private final CancelSaleUseCase cancelSaleUseCase;

    @PostMapping
    public ResponseEntity<SuccessResponse<SaleDocument>> registerNewSale(@RequestBody Sale sale, Principal principal) {
        sale.setIssuedBy(new User(principal.getName()));
        SaleDocument document = createNewSaleUseCase.createNewSale(sale);
        return new ResponseEntity<>(SuccessResponse.created(document), HttpStatus.CREATED);
    }

    @GetMapping("/sessions")
    public ResponseEntity<SuccessResponse<Collection<SaleSession>>> getPageOfSaleSessions(@RequestParam(defaultValue = "") String query,
                                                                                          @RequestParam(defaultValue = "0") int page,
                                                                                          @RequestParam(defaultValue = "10") int size) {
        Page<SaleSession> pageOfSaleSessions = getPageOfSaleSessionsUseCase.getPageOfSaleSessions(query, new Pageable(page, size));
        SuccessResponse.Metadata metadata = PageMapper.mapPage(pageOfSaleSessions);
        return ResponseEntity.ok(SuccessResponse.ok(pageOfSaleSessions.getContent(), metadata));
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<SuccessResponse<SaleSession>> getFullSaleSessionInfo(@RequestHeader("X-Password") String password,
                                                                               @PathVariable Long sessionId) {
        GetFullSaleSessionInfoQuery query = new GetFullSaleSessionInfoQuery(sessionId, password);
        return ResponseEntity.ok(SuccessResponse.ok(getFullSaleSessionInfoUseCase.getFullSaleSessionInfoById(query)));
    }

    @PostMapping("/sessions")
    public ResponseEntity<Void> openSaleSession(@RequestBody SaleSession saleSession) {
        openSaleSessionUseCase.openSaleSession(saleSession);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @PatchMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> closeSaleSession(@PathVariable Long sessionId) {
        closeSaleSessionUseCase.closeSaleSessionById(sessionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{saleId}")
    public ResponseEntity<SuccessResponse<Sale>> getFullSaleInfo(@PathVariable Long saleId) {
        return ResponseEntity.ok(SuccessResponse.ok(getFullSaleInfoUseCase.getFullSaleInfoById(saleId)));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<Collection<Sale>>> getPageOfSales(@RequestParam(defaultValue = "") String query,
                                                                            @RequestParam(defaultValue = "") String type,
                                                                            @RequestParam(defaultValue = "0") int page,
                                                                            @RequestParam(defaultValue = "10") int size) {
        Page<Sale> pageOfSales = getPageOfSalesUseCase.getPageOfSales(query, type, new Pageable(page, size));
        SuccessResponse.Metadata metadata = PageMapper.mapPage(pageOfSales);
        return ResponseEntity.ok(SuccessResponse.ok(pageOfSales.getContent(), metadata));
    }

    @PatchMapping("/{saleId}/cancel")
    public ResponseEntity<Void> cancelSale(@PathVariable Long saleId, Principal principal) {
        cancelSaleUseCase.cancelSaleById(saleId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
