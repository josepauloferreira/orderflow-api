package io.github.josepauloferreira.orderflow.catalog.infrastructure;

import io.github.josepauloferreira.orderflow.catalog.domain.Product;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

  Page<Product> findByActive(boolean active, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select product from Product product where product.id = :id")
  Optional<Product> findByIdForUpdate(@Param("id") UUID id);
}
