package com.dylansbogar.griddybot.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "dealHistory")
public class PostedDeal {

    @Id
    private final String url;

}
