package com.amazons.api;

import com.amazons.api.dto.BotSummaryDto;
import com.amazons.bot.BotRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bots")
public class BotController {

    private final BotRegistry botRegistry;

    public BotController(BotRegistry botRegistry) {
        this.botRegistry = botRegistry;
    }

    @GetMapping
    public List<BotSummaryDto> listBots() {
        return botRegistry.listAll().stream().map(BotSummaryDto::from).toList();
    }
}
