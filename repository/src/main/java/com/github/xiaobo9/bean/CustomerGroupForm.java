/*
 * Copyright 2022 xiaobo9 <https://github.com/xiaobo9>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.xiaobo9.bean;

import com.github.xiaobo9.entity.Contacts;
import com.github.xiaobo9.entity.EntCustomer;

import javax.validation.Valid;

public class CustomerGroupForm {
	@Valid
	private EntCustomer entcustomer ;
	
	@Valid
	private Contacts contacts ;
	
	public EntCustomer getEntcustomer() {
		return entcustomer;
	}
	public void setEntcustomer(EntCustomer entcustomer) {
		this.entcustomer = entcustomer;
	}
	public Contacts getContacts() {
		return contacts;
	}
	public void setContacts(Contacts contacts) {
		this.contacts = contacts;
	}
	
}
