package dev.emly.little_home_library;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;

@Controller
public class KioskController {
	private final LibraryService libraryService;
	private final KioskProperties kioskProperties;

	public KioskController(LibraryService libraryService, KioskProperties kioskProperties) {
		this.libraryService = libraryService;
		this.kioskProperties = kioskProperties;
	}

	@GetMapping("/")
	public String indexRedirect() {
		return "redirect:/kiosk";
	}

	@GetMapping("/kiosk")
	public String viewKiosk(HttpSession session, Model model) {
		String activeUser = (String) session.getAttribute("ACTIVE_USER");
		model.addAttribute("activeUser", activeUser);
		model.addAttribute("autoLogoutMs", kioskProperties.getAutoLogoutMs());

		if (activeUser != null) {
			model.addAttribute("rentals", libraryService.getActiveRentals(activeUser));
			model.addAttribute("lastItem", session.getAttribute("LAST_ITEM"));

			// Pass categories down to fuel the select list elements inside the popup form
			model.addAttribute("categories", kioskProperties.getRentalCategories().keySet());
		}
		return "kiosk";
	}

	@PostMapping("/kiosk/login")
	public String login(@RequestParam String barcode, HttpSession session) {
		String cleanCard = barcode.trim();
		if (cleanCard.isEmpty())
			return "redirect:/kiosk";
		session.removeAttribute("LAST_ITEM");
		session.setAttribute("ACTIVE_USER", cleanCard);
		return "redirect:/kiosk";
	}

	@PostMapping("/kiosk/scan")
	public String scan(@RequestParam String barcode, HttpSession session, RedirectAttributes redirectAttributes) {
		String activeUser = (String) session.getAttribute("ACTIVE_USER");
		if (activeUser == null || barcode.trim().isEmpty())
			return "redirect:/kiosk";

		String b = barcode.trim();

		// Step 1: Detect if barcode metadata exists or can be found on cloud APIs
		ItemDetails item = libraryService.resolveItem(b);
		if (item == null) {
			// Unregistered item detected -> Intercept and trigger the frontend dialog
			// configuration popup!
			redirectAttributes.addFlashAttribute("unknownBarcode", b);
			return "redirect:/kiosk";
		}

		try {
			libraryService.processBarcode(activeUser, b);
			session.setAttribute("LAST_ITEM", item);
		} catch (IllegalStateException e) {
			if (e.getMessage().startsWith("LIMIT_REACHED:")) {
				String[] parts = e.getMessage().split(":");
				redirectAttributes.addFlashAttribute("scanError",
						"🚫 Limit Reached: Maximum of " + parts[1] + " " + parts[2] + "(s) allowed.");
			}
		}
		return "redirect:/kiosk";
	}

	// --- UPDATE THIS METHOD INSIDE KIOSKCONTROLLER.JAVA ---
	@PostMapping("/kiosk/manual-save")
	public String saveManualItem(@RequestParam String barcode, @RequestParam String title,
	                             @RequestParam String author, @RequestParam String category, 
	                             HttpSession session, RedirectAttributes redirectAttributes) {
	    String activeUser = (String) session.getAttribute("ACTIVE_USER");
	    if (activeUser != null && !barcode.trim().isEmpty()) {
	        String cleanBarcode = barcode.trim();
	        try {
	            // Directly fire our unified atomic transaction engine
	            libraryService.processManualItemCheckout(activeUser, cleanBarcode, title.trim(), author.trim(), category.toLowerCase());
	            session.setAttribute("LAST_ITEM", libraryService.resolveItem(cleanBarcode));
	        } catch (IllegalStateException e) {
	            if (e.getMessage().startsWith("LIMIT_REACHED:")) {
	                String[] parts = e.getMessage().split(":");
	                redirectAttributes.addFlashAttribute("scanError", "🚫 Limit Reached: Maximum of " + parts[1] + " " + parts[2] + "(s) allowed.");
	            }
	        }
	    }
	    return "redirect:/kiosk";
	}


	@GetMapping("/kiosk/logout")
	public String logout(HttpSession session) {
		session.invalidate();
		return "redirect:/kiosk";
	}
}
