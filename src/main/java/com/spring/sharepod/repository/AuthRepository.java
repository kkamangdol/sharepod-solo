package com.spring.sharepod.repository;

import com.spring.sharepod.entity.Auth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AuthRepository extends JpaRepository<Auth, Long> {
    @Query("select a from Auth a where a.authbuyer.id=:buyerid")
    List<Auth> findByBuyerId(Long buyerid);

    @Query("select a from Auth a where a.authseller.id=:sellerid")
    List<Auth> findBySellerId(Long sellerid);
}
