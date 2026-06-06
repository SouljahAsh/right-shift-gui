package com.example.almatyclient;

public enum ClientModule {
    SPRINT("sprint", "Sprint") {
        @Override
        public boolean isEnabled() {
            return AlmatyClient.isAutoSprintEnabled();
        }

        @Override
        public void setEnabled(boolean enabled) {
            AlmatyClient.setAutoSprintEnabled(enabled);
        }
    },
    AURA("aura", "Aura") {
        @Override
        public boolean isEnabled() {
            return CombatAutomation.isAuraEnabled();
        }

        @Override
        public void setEnabled(boolean enabled) {
            CombatAutomation.setAuraEnabled(enabled);
        }
    },
    EMERALD_ARMOR_AUTOCRAFT("emeraldArmorAutoCraft", "Emerald Armor AutoCraft") {
        @Override
        public boolean isEnabled() {
            return EmeraldArmorAutomation.isEnabled();
        }

        @Override
        public void setEnabled(boolean enabled) {
            EmeraldArmorAutomation.setEnabled(enabled);
        }
    },
    PARTICLES("particles", "Particles") {
        @Override
        public boolean isEnabled() {
            return AlmatyClient.isParticlesEnabled();
        }

        @Override
        public void setEnabled(boolean enabled) {
            AlmatyClient.setParticlesEnabled(enabled);
        }
    },
    FULLBRIGHT("fullbright", "Fullbright") {
        @Override
        public boolean isEnabled() {
            return AlmatyClient.isFullbrightEnabled();
        }

        @Override
        public void setEnabled(boolean enabled) {
            AlmatyClient.setFullbrightEnabled(enabled);
        }
    },
    ESP("esp", "ESP") {
        @Override
        public boolean isEnabled() {
            return AlmatyClient.isEspEnabled();
        }

        @Override
        public void setEnabled(boolean enabled) {
            AlmatyClient.setEspEnabled(enabled);
        }
    },
    INVENTORY_WALK("inventoryWalk", "InventoryWalk") {
        @Override
        public boolean isEnabled() {
            return AlmatyClient.isInventoryWalkEnabled();
        }

        @Override
        public void setEnabled(boolean enabled) {
            AlmatyClient.setInventoryWalkEnabled(enabled);
        }
    },
    NO_JUMP_DELAY("noJumpDelay", "No Jump Delay") {
        @Override
        public boolean isEnabled() {
            return AlmatyClient.isNoJumpDelayEnabled();
        }

        @Override
        public void setEnabled(boolean enabled) {
            AlmatyClient.setNoJumpDelayEnabled(enabled);
        }
    },
    FAST_PLACE("fastPlace", "FastPlace") {
        @Override
        public boolean isEnabled() {
            return AlmatyClient.isFastPlaceEnabled();
        }

        @Override
        public void setEnabled(boolean enabled) {
            AlmatyClient.setFastPlaceEnabled(enabled);
        }
    },
    AUTO_RESPAWN("autoRespawn", "AutoRespawn") {
        @Override
        public boolean isEnabled() {
            return AlmatyClient.isAutoRespawnEnabled();
        }

        @Override
        public void setEnabled(boolean enabled) {
            AlmatyClient.setAutoRespawnEnabled(enabled);
        }
    };

    private final String id;
    private final String title;

    ClientModule(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public String id() {
        return this.id;
    }

    public String title() {
        return this.title;
    }

    public void toggle() {
        setEnabled(!isEnabled());
    }

    public abstract boolean isEnabled();

    public abstract void setEnabled(boolean enabled);
}
