/* (C)2022 */
package com.example.paymentservice.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_id_generator")
    @SequenceGenerator(
            name = "customer_id_generator",
            sequenceName = "customer_id_seq",
            allocationSize = 100)
    private Long id;

    @Column(nullable = false)
    @NotEmpty(message = "Name cannot be empty")
    private String name;

    private int amountAvailable;

    private int amountReserved;
}
