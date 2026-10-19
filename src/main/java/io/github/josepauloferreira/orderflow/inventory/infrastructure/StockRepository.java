package io.github.josepauloferreira.orderflow.inventory.infrastructure;

import io.github.josepauloferreira.orderflow.inventory.domain.Stock;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, UUID> {}
