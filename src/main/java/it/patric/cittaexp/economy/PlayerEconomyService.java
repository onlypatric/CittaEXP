package it.patric.cittaexp.economy;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerEconomyService {

    private static final DecimalFormat FALLBACK_FORMAT =
            new DecimalFormat("$#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));

    private final JavaPlugin plugin;

    public PlayerEconomyService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean available() {
        return provider() != null;
    }

    public boolean has(Player player, double amount) {
        Object economy = provider();
        if (economy == null || player == null) {
            return false;
        }
        return invokeBoolean(economy, "has", new Class<?>[]{org.bukkit.OfflinePlayer.class, double.class},
                player, Math.max(0.0D, amount));
    }

    public TransactionResult withdraw(Player player, double amount) {
        Object economy = provider();
        if (economy == null) {
            return TransactionResult.failure("vault-unavailable", amount, format(amount));
        }
        if (player == null) {
            return TransactionResult.failure("player-missing", amount, format(amount));
        }
        double safeAmount = Math.max(0.0D, amount);
        Object response = invoke(economy, "withdrawPlayer", new Class<?>[]{org.bukkit.OfflinePlayer.class, double.class}, player, safeAmount);
        if (response == null || !readTransactionSuccess(response)) {
            return TransactionResult.failure(
                    "withdraw-failed",
                    safeAmount,
                    format(safeAmount),
                    response == null ? null : readTransactionError(response)
            );
        }
        return TransactionResult.success(safeAmount, format(safeAmount));
    }

    public TransactionResult deposit(Player player, double amount) {
        Object economy = provider();
        if (economy == null) {
            return TransactionResult.failure("vault-unavailable", amount, format(amount));
        }
        if (player == null) {
            return TransactionResult.failure("player-missing", amount, format(amount));
        }
        double safeAmount = Math.max(0.0D, amount);
        Object response = invoke(economy, "depositPlayer", new Class<?>[]{org.bukkit.OfflinePlayer.class, double.class}, player, safeAmount);
        if (response == null || !readTransactionSuccess(response)) {
            return TransactionResult.failure(
                    "deposit-failed",
                    safeAmount,
                    format(safeAmount),
                    response == null ? null : readTransactionError(response)
            );
        }
        return TransactionResult.success(safeAmount, format(safeAmount));
    }

    public String format(double amount) {
        Object economy = provider();
        if (economy != null) {
            try {
                Object formatted = invoke(economy, "format", new Class<?>[]{double.class}, Math.max(0.0D, amount));
                if (formatted instanceof String string && !string.isBlank()) {
                    return string;
                }
            } catch (RuntimeException ignored) {
            }
        }
        return FALLBACK_FORMAT.format(Math.max(0.0D, amount));
    }

    private Object provider() {
        if (plugin == null || plugin.getServer() == null) {
            return null;
        }
        ServicesManager services = plugin.getServer().getServicesManager();
        if (services == null) {
            return null;
        }
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Object registration = services.getClass()
                    .getMethod("getRegistration", Class.class)
                    .invoke(services, economyClass);
            if (registration == null) {
                return null;
            }
            return registration.getClass().getMethod("getProvider").invoke(registration);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private Object invoke(Object target, String method, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            return null;
        }
        try {
            Method handle = target.getClass().getMethod(method, parameterTypes);
            return handle.invoke(target, args);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private boolean invokeBoolean(Object target, String method, Class<?>[] parameterTypes, Object... args) {
        Object result = invoke(target, method, parameterTypes, args);
        return result instanceof Boolean bool && bool;
    }

    static boolean readTransactionSuccess(Object response) {
        if (response == null) {
            return false;
        }
        try {
            Method method = response.getClass().getMethod("transactionSuccess");
            Object value = method.invoke(response);
            if (value instanceof Boolean bool) {
                return bool;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Field field = response.getClass().getField("transactionSuccess");
            return field.getBoolean(response);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Field typeField = response.getClass().getField("type");
            Object value = typeField.get(response);
            return value != null && "SUCCESS".equalsIgnoreCase(value.toString());
        } catch (ReflectiveOperationException ignored) {
        }
        return false;
    }

    static String readTransactionError(Object response) {
        if (response == null) {
            return null;
        }
        try {
            Method method = response.getClass().getMethod("errorMessage");
            Object value = method.invoke(response);
            if (value != null) {
                return value.toString();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Field field = response.getClass().getField("errorMessage");
            Object value = field.get(response);
            return value == null ? null : value.toString();
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    public record TransactionResult(
            boolean success,
            String reason,
            double amount,
            String formattedAmount,
            String detail
    ) {
        public static TransactionResult success(double amount, String formattedAmount) {
            return new TransactionResult(true, "ok", amount, formattedAmount, null);
        }

        public static TransactionResult failure(String reason, double amount, String formattedAmount) {
            return new TransactionResult(false, reason, amount, formattedAmount, null);
        }

        public static TransactionResult failure(String reason, double amount, String formattedAmount, String detail) {
            return new TransactionResult(false, reason, amount, formattedAmount, detail);
        }
    }
}
