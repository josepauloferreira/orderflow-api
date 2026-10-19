package io.github.josepauloferreira.orderflow.ordering.infrastructure;

import io.github.josepauloferreira.orderflow.ordering.domain.CustomerOrder;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<CustomerOrder, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from CustomerOrder o where o.id = :id")
  Optional<CustomerOrder> findByIdForUpdate(@Param("id") UUID id);
}
