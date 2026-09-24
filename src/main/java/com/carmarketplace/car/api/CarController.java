package com.carmarketplace.car.api;

import com.carmarketplace.car.application.CarService;
import com.carmarketplace.car.domain.Car;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/cars")
public class CarController {

    private final CarService carService;

    @PostMapping
    public ResponseEntity<Car> create(@Valid @RequestBody CreateCarRequest request){
          Car created = carService.saveCar(request.toCar());
          URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                  .path("/{id}")
                  .buildAndExpand(created.id())
                  .toUri();
    return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public Car getCar(@PathVariable String id){
        return carService.getCar(id);
    }
}
