package com.paulfernandosr.possystembackend.guideremission.infrastructure.adapter.input.rest;

import com.paulfernandosr.possystembackend.guideremission.domain.*;
import com.paulfernandosr.possystembackend.guideremission.domain.port.input.GenerateGuideRemissionPdfUseCase;
import com.paulfernandosr.possystembackend.guideremission.domain.port.input.GetGuideRemissionDetailUseCase;
import com.paulfernandosr.possystembackend.guideremission.domain.port.input.ProcessGuideRemissionFullFlowUseCase;
import com.paulfernandosr.possystembackend.guideremission.domain.port.input.QueryGuideRemissionTicketUseCase;
import com.paulfernandosr.possystembackend.guideremission.domain.port.input.RequestGuideRemissionTokenUseCase;
import com.paulfernandosr.possystembackend.guideremission.domain.port.input.SubmitGuideRemissionUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/guide-remissions")
@RequiredArgsConstructor
public class GuideRemissionController {
    private final RequestGuideRemissionTokenUseCase requestGuideRemissionTokenUseCase;
    private final SubmitGuideRemissionUseCase submitGuideRemissionUseCase;
    private final QueryGuideRemissionTicketUseCase queryGuideRemissionTicketUseCase;
    private final ProcessGuideRemissionFullFlowUseCase processGuideRemissionFullFlowUseCase;
    private final GenerateGuideRemissionPdfUseCase generateGuideRemissionPdfUseCase;
    private final GetGuideRemissionDetailUseCase getGuideRemissionDetailUseCase;

    @PostMapping("/token")
    public ResponseEntity<GuideRemissionTokenResponse> requestToken() {
        return ResponseEntity.ok(requestGuideRemissionTokenUseCase.requestToken());
    }

    @PostMapping("/submit")
    public ResponseEntity<GuideRemissionSubmissionResponse> submit(@Valid @RequestBody GuideRemissionSubmission request) {
        return ResponseEntity.ok(submitGuideRemissionUseCase.submit(request));
    }

    @PostMapping("/ticket-status")
    public ResponseEntity<GuideRemissionTicketStatusResponse> queryTicket(@Valid @RequestBody GuideRemissionTicketQuery request) {
        return ResponseEntity.ok(queryGuideRemissionTicketUseCase.queryTicket(request));
    }

    @PostMapping("/full-flow")
    public ResponseEntity<GuideRemissionFullFlowResponse> processFullFlow(@Valid @RequestBody GuideRemissionFullFlowRequest request) {
        return ResponseEntity.ok(processGuideRemissionFullFlowUseCase.process(request));
    }

    @GetMapping("/{serie}/{numero}")
    public ResponseEntity<GuideRemissionDetailResponse> getBySerieAndNumero(@PathVariable String serie, @PathVariable String numero) {
        return ResponseEntity.ok(getGuideRemissionDetailUseCase.getBySerieAndNumero(serie, numero));
    }

    @GetMapping(value = "/{serie}/{numero}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> generatePdf(@PathVariable String serie, @PathVariable String numero) {
        byte[] pdf = generateGuideRemissionPdfUseCase.generate(serie, numero);
        String filename = "GRE-" + serie + "-" + numero + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(pdf);
    }
}
