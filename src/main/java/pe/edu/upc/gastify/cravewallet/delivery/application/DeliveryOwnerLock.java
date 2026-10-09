package pe.edu.upc.gastify.cravewallet.delivery.application;
import java.util.UUID;

public interface DeliveryOwnerLock { void acquire(UUID owner); }
