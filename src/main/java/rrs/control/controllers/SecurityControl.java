package rrs.control.controllers;

import java.io.IOException;
import java.security.Principal;
import java.util.Optional;

import javax.mail.MessagingException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import rrs.model.entities.Account;
import rrs.model.services.FileService;
import rrs.model.services.HttpCookieService;
import rrs.model.utils.FileUpload;
import rrs.model.utils.InterDAO;
import rrs.model.utils.SendMail;
import rrs.utils.CustomException;
import rrs.utils.HTMLUtil;
import rrs.utils.HTMLUtil.BGR;
import rrs.utils.Random;

@Controller
@CrossOrigin("*")
@RequestMapping("/security")
public class SecurityControl {
	
	private final String BACK_HOME = "/pages/home";
	private final String DIRECTORY = "images/account";

	// @formatter:on
	private @Autowired HttpServletRequest req;
	private @Autowired InterDAO<Account, String> dao; // CRUD account
	private @Autowired FileUpload upFile; // Create and delete account's image
	private @Autowired HttpCookieService cookie;
	private @Autowired SendMail mail;
	
	@GetMapping("/{page}") public String pageURI() {
		return new StringBuilder("/pages").append(req.getRequestURI()).toString();
	}
	
	// ___________________________________________________________ LOGIN
	@GetMapping("/loginForm") public String loginForm() {
		return pageURI(); // append "/pages" before URI
	}
	
	@GetMapping("/loginSuccess") public String loginSuccess() {
		Principal principal = req.getUserPrincipal();
		String title = "Đăng nhập tài khoản thành công";
		String message = principal.getName()+" đã đăng nhập thành công";
		req.setAttribute("message", HTMLUtil.alert(BGR.SUCCESS, title, message, 3000));
		req.setAttribute("user", principal);
		
		return BACK_HOME; // back home
	}

	@PostMapping("/loginFailed") public String loginFailed() {
		String title = "Đăng nhập tài khoản thất bại";
		String message = "Tên đăng nhập hoặc mật khẩu không đúng!";
		req.setAttribute("message", HTMLUtil.alert(BGR.WARN, title, message, 3000));
		
		return "/pages/security/loginForm"; // return page
	}

	// ___________________________________________________________ LOGOUT
	@GetMapping("/logoutForm") public String logoutForm() {
		String title = "Chuyển trang đăng xuất";
		String body = "Xác nhận bước tiếp để đăng xuất tài khoản";
		req.setAttribute("message", HTMLUtil.alert(BGR.WARN, title, body, 1000));
		return pageURI();
	}
	
	@GetMapping("/logoutSuccess") public String logoutSuccess() {
		String title = "Đăng xuất tài khoản";
		String message = "Đã đăng xuất tài khoản";
		req.setAttribute("message", HTMLUtil.alert(BGR.LIGHT, title, message, 3000));
		return BACK_HOME;
	}
	
	// _GET -> FORM _____________POST -> SAVE ____________________ SIGN-UP
	@GetMapping("/register") public String register(Model model, @ModelAttribute Account account) {
		model.addAttribute("account", account);
		return pageURI(); // get register form
	}
	
	@PostMapping("/register") public String register(Model model, 
			@RequestParam MultipartFile file, Account account, Errors errors) {
		
		// set account image if file != null
		if(file.getSize() > 0) account.setImage(
			newFileName(account.getUsername(),file.getOriginalFilename())
		);
		
		String message, title = "Đăng ký tài khoản";
		if(!errors.hasErrors())
			try {
				Account a = dao.save(account);
				if(a != null) {
					message = "Đăng ký tài khoản "+a.getUsername()+" thành công";
					model.addAttribute("message", HTMLUtil.alert(BGR.SUCCESS, title, message, 3000));
					if(file.getSize() > 0) upFile.saveFile(account.getImage(), file, this.DIRECTORY);
					return "/pages/security/loginForm"; // load login page
				} 
				message = "Đăng ký tài khoản thất bại!";
				model.addAttribute("message", HTMLUtil.alert(BGR.WARN, title, message, 3000));
			} catch (IllegalArgumentException | CustomException e) {
				model.addAttribute("message", HTMLUtil.alert(BGR.DANGER, title, e.getMessage(), 5000));
			}
		return pageURI(); // callback to re-register input data
	}
	

	// ___________________________________________________________ FORGOT - PASSWORD
	@ResponseBody @RequestMapping("/getCode")
	public ResponseEntity<String> getCodeToMail(@RequestParam String address, HttpServletRequest req) {
		String general = Random.UpperCase("RRs", 8);
		String subject = "Yêu cầu mã xác thực tài khoản!";
		String text = HTMLUtil.getCode("RRs gửi mã xác thực tài khoản", general, "requestresponseser@gmail.com");

		try {
			System.out.println("Send mail code: "+general);
			mail.sendMimeMessage(subject, text, null, null, address);
			cookie.add("general", general, 1); // a minute
		} catch (Exception e) {
			e.printStackTrace();
		}
		return ResponseEntity.ok("[\"RRs đã gửi mã xác thực tới email: "+address+" vui lòng kiểm tra hộp thư.\"]");
	}
	
	@PostMapping("/getPassCode") 
	public String getPassCode(@RequestParam(required = false) String code) {
		Cookie c = cookie.get("code");
		if(code.equals(c.getValue())) 
			return "/pages/security/change_password";
		return "/pages/security/forgot_password";
	}

	// ___________________________________________________________ CHANGE PASSWORD
	@GetMapping("/change_password") public String changePassword(Model model, @ModelAttribute Account account) {
		Principal principal = req.getUserPrincipal();
		Optional<Account> optional = dao.getOptional(principal.getName()); 
		if(optional.isPresent()) {
			Account a = optional.get();
			account.setUsername(a.getUsername());
			account.setPassword(a.getPassword());			
		} model.addAttribute("account", account);
		return this.pageURI();
	}
	
	@PostMapping("/change_password") public String changePassword(@ModelAttribute Account account,
			@RequestParam String newPass, @RequestParam String rePass) {
		String title = "Thay đổi mật khẩu";
		String message = "Nhập lại mật khẩu không đúng!";
		
		if(!rePass.equals(newPass)) req.setAttribute("message", HTMLUtil.alert(BGR.WARN, title, message, 3000));
		else {
			try {
				Optional<Account> optional = dao.getOptional(account.getUsername());
				if(optional.isPresent()) {
					if(account.getPassword().equals(optional.get().getPassword())) {
						message = "Thay đổi mật khẩu thành công";
						req.setAttribute("message", HTMLUtil.alert(BGR.SUCCESS, title, message, 3000));
						account = optional.get();
						account.setPassword(newPass);
						dao.update(account);
						return BACK_HOME;
					}
				} req.setAttribute("message", HTMLUtil.alert(BGR.WHITE, title, "Tài khoản hoặc mật khẩu không đúng", 3000));
			} catch (IllegalArgumentException | CustomException e) {
				e.printStackTrace();
				req.setAttribute("message", HTMLUtil.alert(BGR.WHITE, title, e.getMessage(), 3000));
			}
		} return this.pageURI();
	}
	
	// ___________________________________________________________ UPDATE INFO
	@GetMapping("/about_me") public String getAboutMe(Model model) {
		Principal p = req.getUserPrincipal();
		Optional<Account> optional = dao.getOptional(p.getName());
		model.addAttribute("account", optional.isEmpty() ? new Account() : optional.get());
		return this.pageURI();
	}
	
	@PostMapping("/about_me") public String saveAboutMe(Model model, 
			@RequestParam MultipartFile file, Account account, Errors errors) {
		
		String message, title = "Cập nhật thông tin";
		if(!errors.hasErrors())
			try {
				if(file.getSize() > 0) {
					try {
						upFile.deleteFile(FileService.uri("images/account", account.getImage()));
						account.setImage(newFileName(account.getUsername(),file.getOriginalFilename()));
					} catch (IOException e) {
					}
				}
				
				Account a = dao.update(account);
				if(a != null) {
					message = "Cập nhật tài khoản "+a.getUsername()+" thành công";
					model.addAttribute("message", HTMLUtil.alert(BGR.SUCCESS, title, message, 3000));
					if(file.getSize() > 0) upFile.saveFile(account.getImage(), file, this.DIRECTORY);
				} else {
					message = "Cập nhật thông tin cá nhân thất bại!";
					model.addAttribute("message", HTMLUtil.alert(BGR.WARN, title, message, 3000));
				}
			} catch (IllegalArgumentException | CustomException e) {
				model.addAttribute("message", HTMLUtil.alert(BGR.DANGER, title, e.getMessage(), 5000));
			}
		
		System.out.println(account);
		return this.pageURI(); // callback to about_me page
	}
	
	// @formatter:off
	private String newFileName(String...strs) {
		StringBuilder name = new StringBuilder();
		for(String str : strs) name.append(str);
		return name.hashCode()+name.substring(name.lastIndexOf("."));
	}
}