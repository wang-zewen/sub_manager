package com.submanager.subscriptionmanager.service;

import com.submanager.subscriptionmanager.model.Subscription;
import com.submanager.subscriptionmanager.repository.SubscriptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class SubscriptionService {

    @Autowired
    private SubscriptionRepository repository;

    public List<Subscription> getAllSubscriptions() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public List<Subscription> getActiveSubscriptions() {
        return repository.findByIsActiveOrderByCreatedAtDesc(true);
    }

    public Optional<Subscription> getSubscriptionById(Long id) {
        return repository.findById(id);
    }

    public Subscription createSubscription(Subscription subscription) {
        return repository.save(subscription);
    }

    public Subscription updateSubscription(Long id, Subscription subscription) {
        subscription.setId(id);
        return repository.save(subscription);
    }

    public void deleteSubscription(Long id) {
        repository.deleteById(id);
    }

    public void toggleActiveStatus(Long id) {
        Optional<Subscription> optSub = repository.findById(id);
        optSub.ifPresent(sub -> {
            sub.setIsActive(!sub.getIsActive());
            repository.save(sub);
        });
    }
}
