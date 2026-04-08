package com.betseguras.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    private Telegram telegram = new Telegram();

    public Telegram getTelegram() { return telegram; }
    public void setTelegram(Telegram v) { this.telegram = v; }

    public static class Telegram {
        private boolean enabled = false;
        private String botToken = "";
        /** IDs dos chats/usuários que receberão a notificação */
        private List<String> chatIds = List.of();
        /** Se true, só envia mensagem quando há pelo menos 1 oportunidade */
        private boolean onlyWhenFound = true;

        public boolean isEnabled()               { return enabled; }
        public void setEnabled(boolean v)        { this.enabled = v; }

        public String getBotToken()              { return botToken; }
        public void setBotToken(String v)        { this.botToken = v; }

        public List<String> getChatIds()         { return chatIds; }
        public void setChatIds(List<String> v)   { this.chatIds = v; }

        public boolean isOnlyWhenFound()         { return onlyWhenFound; }
        public void setOnlyWhenFound(boolean v)  { this.onlyWhenFound = v; }
    }
}
