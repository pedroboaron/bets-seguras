package com.betseguras.controller.dto;

/**
 * A single bet to place: where, on what, at what odd, for how much.
 */
public record BetInstruction(
        String casa,
        boolean casaLicenciadaBrasil,
        boolean aceitaPix,
        boolean aceitaBrasileiros,
        String formasDeposito,
        String observacaoCasa,
        String linkFutebol,       // link direto para a seção de futebol da casa
        String resultado,
        double odd,
        double valorAposta
) {}
