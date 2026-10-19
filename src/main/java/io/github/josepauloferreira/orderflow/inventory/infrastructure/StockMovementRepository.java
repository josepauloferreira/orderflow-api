package io.github.josepauloferreira.orderflow.inventory.infrastructure;

import io.github.josepauloferreira.orderflow.inventory.domain.StockMovement;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {}
