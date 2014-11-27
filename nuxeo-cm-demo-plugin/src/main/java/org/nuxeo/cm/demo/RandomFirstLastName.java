/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     thibaud
 */
package org.nuxeo.cm.demo;

/**
 * @author Thibaud Arguillere
 *
 * @since 5.9.2
 */
public class RandomFirstLastName {

    public static final int kMALE = 0;
    public static final int kFEMALE = 1;
    public static final int kMALE_OR_FEMALE = 3;

    private String firstName;
    private String lastName;

    public static final String[] kMALE_FIRST_NAMES = {
        "Andrew", "Anthony", "Brian", "Charles", "Christopher",
        "Daniel", "David", "Dennis", "Donald", "Edward",
        "Eric", "Frank", "Gary", "George", "Gregory",
        "James", "Jason", "Jeffrey", "Jerry",
        "John", "Jose", "Joseph", "Joshua",
        "Kenneth", "Kevin", "Larry", "Mark",
        "Matthew", "Michael", "Patrick", "Paul",
        "Raymond", "Richard", "Robert", "Ronald",
        "Scott", "Stephen", "Steven", "Thomas",
        "Timothy", "Walter", "William"};

    public static final String[] kFEMALE_FIRST_NAMES = {
        "Mary", "Patricia", "Linda", "Barbara",
        "Elizabeth", "Jennifer", "Maria", "Susan",
        "Margaret", "Dorothy", "Lisa", "Nancy",
        "Karen", "Betty", "Helen", "Sandra",
        "Donna", "Carol", "Ruth", "Sharon",
        "Michelle", "Laura", "Sarah", "Kimberly",
        "Deborah", "Jessica", "Shirley", "Cynthia",
        "Angela", "Melissa", "Brenda", "Amy",
        "Anna", "Rebecca", "Virginia", "Kathleen",
        "Pamela", "Martha", "Debra", "Amanda",
        "Stephanie", "Carolyn", "Christine"};

    public static final String[] kLASTNAMES = {
        "Adams", "Aloerd", "Azretty", "Babadero",
        "Babyjaru", "Baeudanin", "Baje", "Baneda",
        "Bannesser", "Bare", "Baschmenn", "Bauzan",
        "Baxoqo", "Beredaeu", "Beuplat", "Biciurt",
        "Bidiwyze", "Biju", "Binucega", "Biuloar",
        "Biurgeux", "Biurgin", "Biuyiu", "Blomin",
        "Boddeu", "Bolalu", "Bonuhode", "Bonumabu",
        "Boruna", "Botokoza", "Bowu", "Bressert",
        "Bubaxiwa", "Bucase", "Bucomydo", "Bumama",
        "Buneqo", "Buruhawa", "Cadobocu", "Caluro",
        "Canydopi", "Caxujy", "Cedasedo", "Celeva",
        "Chentapoa", "Cheona", "Cherrin", "Cucatoxu",
        "Cuqehale", "Cuso", "Cusykuda", "Cyda",
        "Cyladora", "Cylu", "Dabo", "Dabroet",
        "Dadaco", "Dalliya", "Danozit", "Dapu",
        "Daulcaux", "Dedorabu", "Dekabese", "Demalezy",
        "Denavyne", "Denglit", "Denufylu", "Deqe",
        "Desire", "Devubele", "Di Phen", "Didabo",
        "Diguat", "Dingredo", "Docy", "Doluhawa",
        "Domavo", "Donada", "Doruzado", "Dosese",
        "Doziso", "Dubone", "Duraxe", "Dusseult",
        "Dutica", "Elbanqua", "Elfint", "Eot Elo",
        "Fablit", "Febe", "Ferideka", "Fetu"};

    protected static final int kMaleFirstNamesForRandom = kMALE_FIRST_NAMES.length -1;
    protected static final int kFemaleFirstNamesForRandom = kFEMALE_FIRST_NAMES.length -1;
    protected static final int kLastNamesForRandom = kLASTNAMES.length -1;

    private int _randomInt(int inMin, int inMax) {
        return inMin + (int)(Math.random() * ((inMax - inMin) + 1));
    }

    public RandomFirstLastName(int inKind) {
        switch(inKind) {
        case kMALE:
            firstName = kMALE_FIRST_NAMES[ _randomInt(0, kMaleFirstNamesForRandom) ];
            break;

        case kFEMALE:
            firstName = kFEMALE_FIRST_NAMES[ _randomInt(0, kFemaleFirstNamesForRandom) ];
            break;

        default:
            if(_randomInt(0, 1) == 0) {
                firstName = kMALE_FIRST_NAMES[ _randomInt(0, kMaleFirstNamesForRandom) ];
            } else {
                firstName = kFEMALE_FIRST_NAMES[ _randomInt(0, kFemaleFirstNamesForRandom) ];
            }
            break;
        }

        lastName = kLASTNAMES[ _randomInt(0, kLastNamesForRandom) ];

    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }
}
