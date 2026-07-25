package br.com.flaviohblima.lab6rds.controller;

import br.com.flaviohblima.lab6rds.model.OrderEntity;
import br.com.flaviohblima.lab6rds.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderRepository orderRepository;

    @Autowired
    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping
    public List<OrderEntity> byCustomer(@RequestParam(required = false) String customerId) {
        return customerId == null ? orderRepository.findAll()
                : orderRepository.findByCustomerId(customerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderEntity create(@RequestBody CreateOrderRequest createOrderRequest) {
        OrderEntity order = new OrderEntity();
        order.setCustomerId(createOrderRequest.customerId());
        order.setAmount(createOrderRequest.amount());
        order.setStatus("PENDING");
        order.setCreatedAt(Instant.now());
        return orderRepository.save(order);
    }

}
