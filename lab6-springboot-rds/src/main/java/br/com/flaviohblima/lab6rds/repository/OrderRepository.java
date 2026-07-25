package br.com.flaviohblima.lab6rds.repository;

import br.com.flaviohblima.lab6rds.model.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    List<OrderEntity> findByCustomerId(String customerId);

}
