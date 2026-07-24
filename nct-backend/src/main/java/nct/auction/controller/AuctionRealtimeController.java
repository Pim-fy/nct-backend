package nct.auction.controller;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import nct.auction.dto.AuctionRealtimeEvent;
import nct.auction.service.AuctionEventBroker;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class AuctionRealtimeController {

    private final AuctionEventBroker eventBroker;

    @GetMapping(value = "/{auctionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<AuctionRealtimeEvent>>> stream(
            @PathVariable("auctionId") Long auctionId) {
        return ResponseEntity.ok()
                .header("X-Accel-Buffering", "no")
                .body(eventBroker.subscribe(auctionId));
    }

    @ExceptionHandler(IOException.class)
    public void handleClientDisconnect() {
        // SSE 연결을 닫은 브라우저에는 이미 응답이 커밋되어 있어 별도 오류 본문을 쓰지 않는다.
    }
}
