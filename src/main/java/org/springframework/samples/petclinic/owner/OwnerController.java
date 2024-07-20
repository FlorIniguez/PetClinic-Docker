/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.owner;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * @author Juergen Hoeller
 * @author Ken Krebs
 * @author Arjen Poutsma
 * @author Michael Isvy
 */
@Controller
@Slf4j
class OwnerController {

	private static final String VIEWS_OWNER_CREATE_OR_UPDATE_FORM = "owners/createOrUpdateOwnerForm";

	private final OwnerRepository owners;

	public OwnerController(OwnerRepository clinicService) {
		this.owners = clinicService;
	}

	@InitBinder
	public void setAllowedFields(WebDataBinder dataBinder) {
		dataBinder.setDisallowedFields("id");
	}

	//Es una técnica común en Spring MVC para manejar tanto la creación como la edición de entidades en un mismo
	// controlador y formulario. Esto simplifica el código y reduce la duplicación.
	@ModelAttribute("owner")
	public Owner findOwner(@PathVariable(name = "ownerId", required = false) Integer ownerId) {
		return ownerId == null ? new Owner() : this.owners.findById(ownerId);

	}

	@GetMapping("/owners/new")
	public String initCreationForm(Map<String, Object> model) {
		//Toma un Map como parámetro, que se usa para añadir atributos al modelo.
		// El método devuelve un String, que será el nombre de la vista a renderizar.

		//crea un propietario vacio para mostrarlo en el front y se puede llenar desde el form
		Owner owner = new Owner();
		model.put("owner", owner);
		log.info("Starting creation form for a new owner");
		return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
	}

	@PostMapping("/owners/new")
	public String processCreationForm(@Valid Owner owner, BindingResult result, RedirectAttributes redirectAttributes) {
		if (result.hasErrors()) {
			redirectAttributes.addFlashAttribute("error", "There was an error in creating the owner.");

			return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
		}

		this.owners.save(owner);
		redirectAttributes.addFlashAttribute("message", "New Owner Created");
		log.info("An owner was created");
		return "redirect:/owners/" + owner.getId();

	}

	@GetMapping("/owners/find")
	public String initFindForm() {
		return "owners/findOwners";
	}

	@GetMapping("/owners")
	//Ningún owner encontrado: Muestra un mensaje de error.
	//Un solo owner encontrado: Redirige a la página de detalles de ese owner.
	//Múltiples owners encontrados: Muestra una lista paginada de resultados.
	public String processFindForm(@RequestParam(defaultValue = "1") int page, Owner owner, BindingResult result,
								  Model model) {
		// allow parameterless GET request for /owners to return all records
		if (owner.getLastName() == null) {
			owner.setLastName(""); // empty string signifies broadest possible search
		}

		// find owners by last name
		//buscar owners por apellido, con paginación.
		Page<Owner> ownersResults = findPaginatedForOwnersLastName(page, owner.getLastName());
		if (ownersResults.isEmpty()) {
			log.info("No owners found for lastname: {}", owner.getLastName());
			// no owners found
			result.rejectValue("lastName", "notFound", "not found");
			return "owners/findOwners";
		}

//Si se encuentra exactamente un owner: Redirige a la página de detalles de ese owner.
		if (ownersResults.getTotalElements() == 1) {
			// 1 owner found
			owner = ownersResults.iterator().next();
			log.info("An owner was found for last name: {}", owner.getId(), owner.getLastName());
			return "redirect:/owners/" + owner.getId();
		}

		// multiple owners found
		log.info("{} owners found for last name '{}'. Showing page {} of results",
			ownersResults.getTotalElements(), owner.getLastName(), page);
		//log aca sirve para:
		//Monitorear el uso de la función de búsqueda. Identificar patrones en las búsquedas de los usuarios.
		//Depurar problemas relacionados con la paginación.Analizar la eficacia de la función de búsqueda
		return addPaginationModel(page, model, ownersResults);
	}

	// Este método es principalmente para preparar el modelo para la vista.
	//EN ESTOS METODOS PODRIA PONER LOGS DE DEBUG
	private String addPaginationModel(int page, Model model, Page<Owner> paginated) {
		List<Owner> listOwners = paginated.getContent();
		model.addAttribute("currentPage", page);
		model.addAttribute("totalPages", paginated.getTotalPages());
		model.addAttribute("totalItems", paginated.getTotalElements());
		model.addAttribute("listOwners", listOwners);
		return "owners/ownersList";
	}

	private Page<Owner> findPaginatedForOwnersLastName(int page, String lastname) {
		int pageSize = 5;
		Pageable pageable = PageRequest.of(page - 1, pageSize);
		return owners.findByLastName(lastname, pageable);
	}

	@GetMapping("/owners/{ownerId}/edit")
	public String initUpdateOwnerForm(@PathVariable("ownerId") int ownerId, Model model) {
		Owner owner = this.owners.findById(ownerId);
		//Añade el owner encontrado al modelo. Esto permite que la información del owner esté disponible en la vista para ser editada.
		model.addAttribute(owner);
		log.info("Starting update form for owner with ID: {}", ownerId);
		return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
	}

	@PostMapping("/owners/{ownerId}/edit")
	public String processUpdateOwnerForm(@Valid Owner owner, BindingResult result, @PathVariable("ownerId") int ownerId,
										 RedirectAttributes redirectAttributes) {
		log.info("Iniciando actualización del propietario con ID: {}", ownerId);
		if (result.hasErrors()) {
			log.warn("Starting owner update with ID: {}", ownerId);
			redirectAttributes.addFlashAttribute("error", "There was an error in updating the owner.");
			return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
		}

		owner.setId(ownerId);
		this.owners.save(owner);
		log.info("Owner with ID:{} updated ok", ownerId);
		redirectAttributes.addFlashAttribute("message", "Owner Values Updated");

		return "redirect:/owners/{ownerId}";
	}

	/**
	 * Custom handler for displaying an owner.
	 *
	 * @param ownerId the ID of the owner to display
	 * @return a ModelMap with the model attributes for the view
	 */
	@GetMapping("/owners/{ownerId}")
	public ModelAndView showOwner(@PathVariable("ownerId") int ownerId) {
		//Crea un nuevo ModelAndView, especificando "owners/ownerDetails" como la vista a usar.
		log.info("Mostrando detalles del Owner con ID: {}", ownerId);
		ModelAndView mav = new ModelAndView("owners/ownerDetails");
		Owner owner = this.owners.findById(ownerId);
		//Añade el objeto owner al modelo. Esto hace que los datos del owner estén disponibles en la vista.
		mav.addObject(owner);
		return mav;
	}

}
