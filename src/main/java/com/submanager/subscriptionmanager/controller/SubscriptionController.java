package com.submanager.subscriptionmanager.controller;

import com.submanager.subscriptionmanager.model.Subscription;
import com.submanager.subscriptionmanager.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/")
public class SubscriptionController {

    @Autowired
    private SubscriptionService subscriptionService;

    @GetMapping
    public String index(Model model) {
        List<Subscription> subscriptions = subscriptionService.getAllSubscriptions();
        model.addAttribute("subscriptions", subscriptions);
        model.addAttribute("subscription", new Subscription());
        return "index";
    }

    @PostMapping("/subscriptions")
    public String createSubscription(@Valid @ModelAttribute("subscription") Subscription subscription,
                                    BindingResult result,
                                    RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Please fill all required fields");
            return "redirect:/";
        }

        subscriptionService.createSubscription(subscription);
        redirectAttributes.addFlashAttribute("success", "Subscription created successfully");
        return "redirect:/";
    }

    @PostMapping("/subscriptions/{id}/delete")
    public String deleteSubscription(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        subscriptionService.deleteSubscription(id);
        redirectAttributes.addFlashAttribute("success", "Subscription deleted successfully");
        return "redirect:/";
    }

    @PostMapping("/subscriptions/{id}/toggle")
    public String toggleSubscription(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        subscriptionService.toggleActiveStatus(id);
        redirectAttributes.addFlashAttribute("success", "Subscription status updated");
        return "redirect:/";
    }

    @GetMapping("/subscriptions/{id}/edit")
    public String editSubscription(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return subscriptionService.getSubscriptionById(id)
                .map(subscription -> {
                    model.addAttribute("subscription", subscription);
                    model.addAttribute("subscriptions", subscriptionService.getAllSubscriptions());
                    model.addAttribute("editMode", true);
                    return "index";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("error", "Subscription not found");
                    return "redirect:/";
                });
    }

    @PostMapping("/subscriptions/{id}")
    public String updateSubscription(@PathVariable Long id,
                                    @Valid @ModelAttribute("subscription") Subscription subscription,
                                    BindingResult result,
                                    RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Please fill all required fields");
            return "redirect:/";
        }

        subscriptionService.updateSubscription(id, subscription);
        redirectAttributes.addFlashAttribute("success", "Subscription updated successfully");
        return "redirect:/";
    }
}
