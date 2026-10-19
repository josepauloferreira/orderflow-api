package io.github.josepauloferreira.orderflow.catalog.infrastructure;

import io.github.josepauloferreira.orderflow.catalog.domain.Product;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, UUID> {

  Page<Product> findByActive(boolean active, Pageable pageable);
}
