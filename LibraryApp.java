// =====================================================================
// LibraryApp.java - Quản lý Thư viện - ĐH Phú Xuân
// FIXED: C.1 C.2 C.3 C.4 D.1 E.1 E.4
// =====================================================================
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.print.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

// =====================================================================
// 0. TIME UTIL - GMT+7
// =====================================================================
class TimeUtil {
    static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    static final DateTimeFormatter D  = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static LocalDateTime now()   { return LocalDateTime.now(VN); }
    static LocalDate     today() { return LocalDate.now(VN); }              // FIX C.1
    static String fmt(LocalDateTime t) { return t == null ? "" : DT.format(t); }
    static String fmt(LocalDate d)     { return d == null ? "" : D.format(d); }
    static String money(double v)      { return String.format("%,.0f đ", v); }
}

// =====================================================================
// 1. EXCEPTIONS
// =====================================================================
class LibraryException extends Exception { LibraryException(String m){super(m);} }
class InvalidInputException extends IllegalArgumentException { InvalidInputException(String m){super(m);} }
class DuplicateIdException extends LibraryException { DuplicateIdException(String id){super("Mã đã tồn tại: "+id);} }
class ItemNotFoundException extends LibraryException { ItemNotFoundException(String id){super("Không tìm thấy: "+id);} }
class ItemNotAvailableException extends LibraryException { ItemNotAvailableException(String id){super("Item không khả dụng: "+id);} }
class LoanLimitExceededException extends LibraryException { LoanLimitExceededException(int m){super("Đã đạt giới hạn mượn ("+m+" item).");} }
class HasOverdueLoanException extends LibraryException { HasOverdueLoanException(){super("Độc giả còn phiếu quá hạn chưa trả.");} }
class CardExpiredException extends LibraryException { CardExpiredException(){super("Thẻ độc giả đã hết hạn hoặc chưa đăng ký.");} }
class CannotBorrowException extends LibraryException { CannotBorrowException(String m){super(m);} }
class ItemInUseException extends LibraryException { ItemInUseException(){super("Item đang được mượn, không thể xóa.");} }
class ReaderInUseException extends LibraryException { ReaderInUseException(){super("Độc giả đang có phiếu mượn, không thể xóa.");} }
class DuplicateLoanException extends LibraryException { DuplicateLoanException(){super("Độc giả đã mượn item này rồi.");} }
class LoanNotFoundException extends LibraryException { LoanNotFoundException(String id){super("Không tìm thấy phiếu: "+id);} }
class RenewNotAllowedException extends LibraryException { RenewNotAllowedException(String m){super(m);} }

// =====================================================================
// 2. ENUMS
// =====================================================================
enum CardType {
    NONE("Không có thẻ", 0, 0, false),
    READING("Thẻ đọc", 50000, 0, false),
    BORROW_ADULT("Thẻ mượn - Người lớn", 200000, 5, true),
    BORROW_STUDENT("Thẻ mượn - HSSV", 100000, 5, true);
    private final String label;
    private final double annualFee;
    private final int maxLoans;
    private final boolean canBorrow;
    CardType(String label, double annualFee, int maxLoans, boolean canBorrow) {
        this.label=label; this.annualFee=annualFee; this.maxLoans=maxLoans; this.canBorrow=canBorrow;
    }
    public String getLabel(){return label;}
    public double getAnnualFee(){return annualFee;}
    public int getMaxLoans(){return maxLoans;}
    public boolean canBorrow(){return canBorrow;}
    @Override public String toString(){return label;}
}

enum LoanStatus {
    BORROWING("Đang mượn"), RETURNED("Đã trả"), OVERDUE("Quá hạn"), LOST("Mất sách");
    private final String label;
    LoanStatus(String l){this.label=l;}
    @Override public String toString(){return label;}
}

enum ItemEventType {
    ADDED("Nhập"), DELETED("Xoá"), UPDATED("Cập nhật");
    private final String label;
    ItemEventType(String l){this.label=l;}
    public String getLabel(){return label;}
}

enum PeriodFilter {
    TODAY("Hôm nay"), WEEK("Tuần này (7 ngày)"), MONTH("Tháng này"), YEAR("Năm nay"), ALL("Tất cả");
    private final String label;
    PeriodFilter(String l){this.label=l;}
    @Override public String toString(){return label;}
}

// =====================================================================
// 2.5 INTERFACE - FIX E.1 (ISP + đa hình theo hợp đồng mượn)
// =====================================================================
interface Borrowable {
    boolean canBeBorrowedBy(Reader reader);
}

// =====================================================================
// 3. MODEL: ITEM
// =====================================================================
abstract class Item implements Borrowable {  // FIX E.1
    protected String id, title, publisher;
    protected int publishYear, edition, totalCopies, availableCopies;
    protected double price;
    protected LocalDate importDate;
    protected LocalDateTime createdAt;

    protected Item(String id, String title, String publisher, int publishYear,
                   int edition, double price, int totalCopies) {
        if (id == null || id.isBlank()) throw new InvalidInputException("Mã không được rỗng");
        if (title == null || title.isBlank()) throw new InvalidInputException("Tên không được rỗng");
        if (publisher == null || publisher.isBlank()) throw new InvalidInputException("NXB không được rỗng");
        if (publishYear < 1800 || publishYear > TimeUtil.today().getYear())        // FIX C.1
            throw new InvalidInputException("Năm XB không hợp lệ");
        if (edition < 1) throw new InvalidInputException("Lần XB phải >= 1");
        if (price <= 0) throw new InvalidInputException("Giá tiền phải > 0");
        if (totalCopies < 1) throw new InvalidInputException("Tổng bản phải >= 1");
        this.id=id; this.title=title; this.publisher=publisher;
        this.publishYear=publishYear; this.edition=edition;
        this.price=price; this.totalCopies=totalCopies;
        this.availableCopies=totalCopies;
        this.importDate=TimeUtil.today();                                           // FIX C.1
        this.createdAt=TimeUtil.now();
    }

    public abstract String getType();
    public abstract int getMaxLoanDays();
    public abstract double calculateLateFee(int daysLate);

    // FIX D.1 - helper dùng chung cho 3 loại (Sách/Tạp chí/DVD)
    // Công thức: dưới 100k → lowRate/ngày; từ 100k → highRate/ngày
    protected double standardLateFee(int daysLate, double lowRate, double highRate) {
        if (daysLate <= 0) return 0;
        return daysLate * (price < 100000 ? lowRate : highRate);
    }

    // FIX E.1 - mặc định: chỉ cần còn hàng + có hạn mượn > 0
    @Override
    public boolean canBeBorrowedBy(Reader reader) {
        return getMaxLoanDays() > 0 && isAvailable();
    }

    public boolean isAvailable() { return availableCopies > 0; }
    public boolean canBeBorrowed() { return getMaxLoanDays() > 0; }
    public void borrowOne() throws ItemNotAvailableException {
        if (availableCopies <= 0) throw new ItemNotAvailableException(id);
        availableCopies--;
    }
    public void returnOne() { if (availableCopies < totalCopies) availableCopies++; }
    void setAvailableCopies(int n) { this.availableCopies = Math.max(0, Math.min(n, totalCopies)); }

    public String getId(){return id;} public String getTitle(){return title;}
    public String getPublisher(){return publisher;} public int getPublishYear(){return publishYear;}
    public int getEdition(){return edition;} public double getPrice(){return price;}
    public int getTotalCopies(){return totalCopies;} public int getAvailableCopies(){return availableCopies;}
    public LocalDate getImportDate(){return importDate;} public LocalDateTime getCreatedAt(){return createdAt;}
    @Override public String toString(){ return id + " - " + title; }
}

class Book extends Item {
    private String author, isbn, genre;
    public Book(String id, String t, String p, int y, int ed, double pr, int cp,
                String author, String isbn, String genre) {
        super(id,t,p,y,ed,pr,cp);
        if (author==null||author.isBlank()) throw new InvalidInputException("Tác giả không được rỗng");
        this.author=author; this.isbn=isbn==null?"":isbn; this.genre=genre==null?"":genre;
    }
    @Override public String getType(){return "Sách";}
    @Override public int getMaxLoanDays(){return 30;}                                // FIX E.4
    @Override public double calculateLateFee(int d){ return standardLateFee(d, 1000, 2000); } // FIX D.1
    public String getAuthor(){return author;} public String getIsbn(){return isbn;} public String getGenre(){return genre;}
}

class Magazine extends Item {
    private int issueNumber; private String period;
    public Magazine(String id, String t, String p, int y, int ed, double pr, int cp,
                    int issueNumber, String period) {
        super(id,t,p,y,ed,pr,cp);
        if (issueNumber<1) throw new InvalidInputException("Số phát hành phải >= 1");
        this.issueNumber=issueNumber; this.period=period==null?"":period;
    }
    @Override public String getType(){return "Tạp chí";}
    @Override public int getMaxLoanDays(){return 14;}                                // FIX E.4
    @Override public double calculateLateFee(int d){ return standardLateFee(d, 500, 1000); } // FIX D.1+E.4
    public int getIssueNumber(){return issueNumber;} public String getPeriod(){return period;}
}

class DVD extends Item {
    private int durationMinutes; private String director;
    public DVD(String id, String t, String p, int y, int ed, double pr, int cp,
               int dur, String director) {
        super(id,t,p,y,ed,pr,cp);
        if (dur<=0) throw new InvalidInputException("Thời lượng phải > 0");
        this.durationMinutes=dur; this.director=director==null?"":director;
    }
    @Override public String getType(){return "DVD";}
    @Override public int getMaxLoanDays(){return 7;}                                 // FIX E.4
    @Override public double calculateLateFee(int d){ return standardLateFee(d, 2000, 3000); } // FIX D.1+E.4
    public int getDurationMinutes(){return durationMinutes;} public String getDirector(){return director;}
}

class Thesis extends Item {
    private String studentName, supervisor, degree;
    public Thesis(String id, String t, String p, int y, int ed, double pr, int cp,
                  String sv, String gvhd, String degree) {
        super(id,t,p,y,ed,pr,cp);
        if (sv==null||sv.isBlank()) throw new InvalidInputException("Tên học viên không được rỗng");
        this.studentName=sv; this.supervisor=gvhd==null?"":gvhd; this.degree=degree==null?"":degree;
    }
    @Override public String getType(){return "Luận văn";}
    @Override public int getMaxLoanDays(){return 0;}
    @Override public double calculateLateFee(int d){return 0;}
    @Override public boolean canBeBorrowedBy(Reader r){ return false; }              // FIX E.1
    public String getStudentName(){return studentName;} public String getSupervisor(){return supervisor;}
    public String getDegree(){return degree;}
}

// =====================================================================
// 4. READER + PAYMENT
// =====================================================================
class PaymentRecord {
    private final LocalDateTime timestamp;
    private final double amount;
    private final String description;
    PaymentRecord(double amount, String desc) {
        this.timestamp=TimeUtil.now(); this.amount=amount; this.description=desc;
    }
    public LocalDateTime getTimestamp(){return timestamp;}
    public double getAmount(){return amount;}
    public String getDescription(){return description;}
}

class Reader {
    private String id, fullName, phone, email, address;
    private LocalDate dateOfBirth;
    private CardType cardType;
    private boolean hasPhysicalCard;
    private LocalDate subscriptionStart, subscriptionEnd;
    private final List<Loan> history = new ArrayList<>();
    private final List<PaymentRecord> payments = new ArrayList<>();

    public Reader(String id, String fullName, CardType type) {
        if (id==null||id.isBlank()) throw new InvalidInputException("Mã không được rỗng");
        if (fullName==null||fullName.isBlank()) throw new InvalidInputException("Họ tên không được rỗng");
        if (type==null) throw new InvalidInputException("Loại thẻ không hợp lệ");
        this.id=id; this.fullName=fullName; this.cardType=type;
        this.hasPhysicalCard = type != CardType.NONE;
    }

    public void subscribe(int months, double fee) {
        if (months<=0) throw new InvalidInputException("Số tháng phải > 0");
        LocalDate today = TimeUtil.today();                                         // FIX C.1
        LocalDate base = (subscriptionEnd==null || subscriptionEnd.isBefore(today))
                ? today : subscriptionEnd;
        this.subscriptionStart = subscriptionStart==null ? today : subscriptionStart;
        this.subscriptionEnd = base.plusMonths(months);
        addPayment(fee, "Gói " + cardType.getLabel() + " - " + months + " tháng");
    }

    // FIX C.2 - đổi loại thẻ đúng cách
    public void changeCardType(CardType newType) {
        if (newType == null) throw new InvalidInputException("Loại thẻ không hợp lệ");
        this.cardType = newType;
        if (newType != CardType.NONE && !this.hasPhysicalCard) this.hasPhysicalCard = true;
    }

    public void addPayment(double amount, String desc) {
        if (amount > 0) payments.add(new PaymentRecord(amount, desc));
    }

    public boolean isActive() {
        return cardType != CardType.NONE && subscriptionEnd != null
                && !TimeUtil.today().isAfter(subscriptionEnd);                       // FIX C.1
    }
    public boolean canBorrow() { return isActive() && cardType.canBorrow(); }
    public int getMaxLoans() { return cardType.getMaxLoans(); }

    public List<Loan> getActiveLoans() {
        return history.stream()
            .filter(l -> l.getStatus()==LoanStatus.BORROWING || l.getStatus()==LoanStatus.OVERDUE)
            .collect(Collectors.toList());
    }
    public int getActiveLoanCount() { return getActiveLoans().size(); }
    public boolean hasOverdueLoan() { return getActiveLoans().stream().anyMatch(Loan::isOverdue); }
    public boolean hasLoanOfItem(Item item) {
        return getActiveLoans().stream().anyMatch(l -> l.getItem().getId().equals(item.getId()));
    }
    public void addLoan(Loan l) { history.add(l); }

    public void setFullName(String n){ if(n==null||n.isBlank()) throw new InvalidInputException("Họ tên rỗng"); this.fullName=n; }
    public void setDateOfBirth(LocalDate d){this.dateOfBirth=d;}
    public void setPhone(String p){this.phone=p;}
    public void setEmail(String e){this.email=e;}
    public void setAddress(String a){this.address=a;}
    public void setHasPhysicalCard(boolean h){this.hasPhysicalCard=h;}

    public String getId(){return id;} public String getFullName(){return fullName;}
    public String getPhone(){return phone;} public String getEmail(){return email;}
    public String getAddress(){return address;} public LocalDate getDateOfBirth(){return dateOfBirth;}
    public CardType getCardType(){return cardType;} public boolean hasPhysicalCard(){return hasPhysicalCard;}
    public LocalDate getSubscriptionStart(){return subscriptionStart;}
    public LocalDate getSubscriptionEnd(){return subscriptionEnd;}
    public List<Loan> getHistory(){return history;}
    public List<PaymentRecord> getPayments(){return payments;}
    public double getTotalPaid() { return payments.stream().mapToDouble(PaymentRecord::getAmount).sum(); }
    @Override public String toString(){ return id + " - " + fullName; }
}

// =====================================================================
// 5. LOAN
// =====================================================================
class Loan {
    private final String loanId;
    private final Item item;
    private final Reader reader;
    private final LocalDate borrowDate;
    private LocalDate dueDate, returnDate;
    private LoanStatus status;
    private double paidFee;

    public Loan(String loanId, Item item, Reader reader, LocalDate borrowDate, LocalDate customDueDate) {
        // FIX C.4 - validate due date
        if (customDueDate != null && customDueDate.isBefore(borrowDate))
            throw new InvalidInputException("Hạn trả không được trước ngày mượn");
        this.loanId=loanId; this.item=item; this.reader=reader;
        this.borrowDate=borrowDate;
        this.dueDate = customDueDate != null ? customDueDate
                : borrowDate.plusDays(item.getMaxLoanDays());                       // FIX C.4
        this.status=LoanStatus.BORROWING; this.paidFee=0;
    }

    public boolean isOverdue() {
        if (status==LoanStatus.RETURNED||status==LoanStatus.LOST) return false;
        return TimeUtil.today().isAfter(dueDate);                                   // FIX C.1
    }
    public int getDaysLate() {
        LocalDate end = returnDate != null ? returnDate : TimeUtil.today();         // FIX C.1
        if (!end.isAfter(dueDate)) return 0;
        return (int) ChronoUnit.DAYS.between(dueDate, end);
    }
    public double getLateFee() {
        if (status==LoanStatus.LOST) return item.getPrice() * 2;
        return item.calculateLateFee(getDaysLate());
    }
    public LoanStatus getDisplayStatus() {
        if (status==LoanStatus.BORROWING && isOverdue()) return LoanStatus.OVERDUE;
        return status;
    }
    public void returnItem() {
        if (status==LoanStatus.RETURNED||status==LoanStatus.LOST)
            throw new IllegalStateException("Phiếu đã kết thúc");
        this.returnDate=TimeUtil.today();                                           // FIX C.1
        this.paidFee=getLateFee();
        this.status=LoanStatus.RETURNED;
        item.returnOne();
        if (paidFee>0) reader.addPayment(paidFee, "Phí phạt trả trễ phiếu " + loanId);
    }
    public void markLost() {
        if (status==LoanStatus.RETURNED||status==LoanStatus.LOST)
            throw new IllegalStateException("Phiếu đã kết thúc");
        this.status=LoanStatus.LOST;
        this.paidFee=getLateFee();
        reader.addPayment(paidFee, "Đền mất sách phiếu " + loanId);
    }
    public void renew() throws RenewNotAllowedException {
        if (status!=LoanStatus.BORROWING) throw new RenewNotAllowedException("Chỉ gia hạn khi đang mượn");
        if (isOverdue()) throw new RenewNotAllowedException("Không thể gia hạn khi đã quá hạn");
        dueDate = dueDate.plusDays(item.getMaxLoanDays());
    }
    public String getLoanId(){return loanId;} public Item getItem(){return item;} public Reader getReader(){return reader;}
    public LocalDate getBorrowDate(){return borrowDate;} public LocalDate getDueDate(){return dueDate;}
    public LocalDate getReturnDate(){return returnDate;} public LoanStatus getStatus(){return status;}
    public double getPaidFee(){return paidFee;}
}

// =====================================================================
// 6. ITEM EVENT
// =====================================================================
class ItemEvent {
    private final LocalDateTime timestamp;
    private final ItemEventType type;
    private final String itemId, itemTitle, itemType, note;
    ItemEvent(ItemEventType type, Item item) {
        this.timestamp=TimeUtil.now(); this.type=type;
        this.itemId=item.getId(); this.itemTitle=item.getTitle(); this.itemType=item.getType();
        this.note = type.getLabel() + " item " + itemId;
    }
    public LocalDateTime getTimestamp(){return timestamp;}
    public ItemEventType getType(){return type;}
    public String getItemId(){return itemId;} public String getItemTitle(){return itemTitle;}
    public String getItemType(){return itemType;} public String getNote(){return note;}
}

// =====================================================================
// 7. ID GENERATOR
// =====================================================================
class IdGenerator {
    public static String nextItemId(Class<? extends Item> type, List<Item> items) {
        String prefix;
        if (type==Book.class) prefix="B";
        else if (type==Magazine.class) prefix="M";
        else if (type==DVD.class) prefix="D";
        else if (type==Thesis.class) prefix="L";
        else throw new InvalidInputException("Loại item không hợp lệ");
        int max=0;
        for (Item it : items) if (it.getId().startsWith(prefix)) {
            try { max=Math.max(max, Integer.parseInt(it.getId().substring(1))); }
            catch (NumberFormatException ignored) {}
        }
        return String.format("%s%04d", prefix, max+1);
    }
    public static String nextReaderId(List<Reader> readers) {
        int max=0;
        for (Reader r : readers) if (r.getId().startsWith("R")) {
            try { max=Math.max(max, Integer.parseInt(r.getId().substring(1))); }
            catch (NumberFormatException ignored) {}
        }
        return String.format("R%04d", max+1);
    }
    public static String nextLoanId(List<Loan> loans, LocalDate date) {
        String prefix = String.format("L%02d%04d", date.getMonthValue(), date.getYear());
        int max = 0;
        for (Loan l : loans) if (l.getLoanId().startsWith(prefix)) {
            try { max = Math.max(max, Integer.parseInt(l.getLoanId().substring(prefix.length()))); }
            catch (NumberFormatException ignored) {}
        }
        return String.format("%s%04d", prefix, max + 1);
    }
}

// =====================================================================
// 8. SERVICE
// =====================================================================
class LibraryService {
    public static final double HARD_CARD_FEE = 20000.0;
    private final List<Item> items = new ArrayList<>();
    private final List<Reader> readers = new ArrayList<>();
    private final List<Loan> loans = new ArrayList<>();
    private final List<ItemEvent> itemEvents = new ArrayList<>();

    public List<Item> getAllItems(){return Collections.unmodifiableList(items);}
    public List<Reader> getAllReaders(){return Collections.unmodifiableList(readers);}
    public List<Loan> getAllLoans(){return Collections.unmodifiableList(loans);}
    public List<ItemEvent> getAllItemEvents(){return Collections.unmodifiableList(itemEvents);}

    public Item findItem(String id){ if(id==null)return null; for(Item i:items) if(i.getId().equalsIgnoreCase(id.trim()))return i; return null; }
    public Reader findReader(String id){ if(id==null)return null; for(Reader r:readers) if(r.getId().equalsIgnoreCase(id.trim()))return r; return null; }
    public Loan findLoan(String id){ if(id==null)return null; for(Loan l:loans) if(l.getLoanId().equalsIgnoreCase(id.trim()))return l; return null; }

    // ITEM
    public void addItem(Item it) throws DuplicateIdException {
        if (findItem(it.getId())!=null) throw new DuplicateIdException(it.getId());
        items.add(it); itemEvents.add(new ItemEvent(ItemEventType.ADDED, it));
    }
    public void updateItem(Item updated) throws LibraryException {
        for (int i=0;i<items.size();i++) {
            Item old=items.get(i);
            if (old.getId().equals(updated.getId())) {
                int borrowed = old.getTotalCopies()-old.getAvailableCopies();
                if (updated.getTotalCopies()<borrowed)
                    throw new LibraryException("Không thể giảm tổng bản dưới "+borrowed+" (đang mượn)");
                updated.setAvailableCopies(updated.getTotalCopies()-borrowed);
                items.set(i, updated);
                itemEvents.add(new ItemEvent(ItemEventType.UPDATED, updated));
                return;
            }
        }
        throw new ItemNotFoundException(updated.getId());
    }
    public void deleteItem(String id) throws LibraryException {
        Item it = findItem(id);
        if (it==null) throw new ItemNotFoundException(id);
        for (Loan l : loans)
            if (l.getItem().getId().equals(id)
                    && (l.getStatus()==LoanStatus.BORROWING||l.getStatus()==LoanStatus.OVERDUE))
                throw new ItemInUseException();
        items.remove(it);
        itemEvents.add(new ItemEvent(ItemEventType.DELETED, it));
    }
    public List<Item> searchItems(String kw, String typeFilter) {
        String k = kw==null?"":kw.trim().toLowerCase();
        return items.stream()
            .filter(i -> k.isEmpty() || i.getTitle().toLowerCase().contains(k) || i.getId().toLowerCase().contains(k))
            .filter(i -> typeFilter==null || typeFilter.equals("Tất cả") || i.getType().equals(typeFilter))
            .collect(Collectors.toList());
    }
    public String suggestNextItemId(Class<? extends Item> t) { return IdGenerator.nextItemId(t, items); }

    // READER
    public void addReader(Reader r) throws DuplicateIdException {
        if (findReader(r.getId())!=null) throw new DuplicateIdException(r.getId());
        readers.add(r);
    }
    public void updateReader(Reader upd) throws LibraryException {
        Reader r = findReader(upd.getId());
        if (r==null) throw new ItemNotFoundException(upd.getId());
        r.setFullName(upd.getFullName()); r.setDateOfBirth(upd.getDateOfBirth());
        r.setPhone(upd.getPhone()); r.setEmail(upd.getEmail()); r.setAddress(upd.getAddress());
        r.setHasPhysicalCard(upd.hasPhysicalCard());
    }
    public void deleteReader(String id) throws LibraryException {
        Reader r = findReader(id);
        if (r==null) throw new ItemNotFoundException(id);
        if (!r.getActiveLoans().isEmpty()) throw new ReaderInUseException();
        readers.remove(r);
    }
    public String suggestNextReaderId() { return IdGenerator.nextReaderId(readers); }

    // FEE CALC
    public double calcNewRegistrationFee(CardType type, int months, boolean wantsHardCard) {
        if (type==CardType.NONE || months<=0) return 0;
        double fee = type.getAnnualFee()/12.0*months;
        if (wantsHardCard) fee += HARD_CARD_FEE;
        return fee;
    }
    public double calcRenewalFee(CardType type, int months) {
        if (type==CardType.NONE || months<=0) return 0;
        return type.getAnnualFee()/12.0*months;
    }

    // LOAN
    public Loan borrow(String rid, String iid, LocalDate customDueDate) throws LibraryException {
        Reader r = findReader(rid);
        if (r==null) throw new ItemNotFoundException("Không tìm thấy độc giả: "+rid);
        Item it = findItem(iid);
        if (it==null) throw new ItemNotFoundException("Không tìm thấy item: "+iid);
        if (!r.isActive()) throw new CardExpiredException();
        if (!r.getCardType().canBorrow()) throw new CannotBorrowException("Thẻ \""+r.getCardType().getLabel()+"\" không được mượn.");
        if (r.hasOverdueLoan()) throw new HasOverdueLoanException();
        if (r.getActiveLoanCount() >= r.getMaxLoans())
            throw new LoanLimitExceededException(r.getMaxLoans());
        if (r.hasLoanOfItem(it)) throw new DuplicateLoanException();
        if (!it.canBeBorrowedBy(r)) throw new ItemNotAvailableException("Loại này không cho mượn: "+iid);  // FIX E.1

        // FIX C.3 - validate hạn trả dựa trên loại item (không hard-code 30)
        LocalDate borrowDate = TimeUtil.today();
        LocalDate dueDate = customDueDate != null ? customDueDate
                : borrowDate.plusDays(it.getMaxLoanDays());
        if (dueDate.isBefore(borrowDate))
            throw new InvalidInputException("Hạn trả không được trước hôm nay");
        if (dueDate.isAfter(borrowDate.plusDays(it.getMaxLoanDays())))
            throw new InvalidInputException("Hạn trả vượt quá " + it.getMaxLoanDays()
                    + " ngày (theo loại " + it.getType() + ").");

        it.borrowOne();
        Loan l = new Loan(IdGenerator.nextLoanId(loans, borrowDate), it, r, borrowDate, dueDate);
        loans.add(l); r.addLoan(l);
        return l;
    }
    public void returnLoan(String lid) throws LibraryException {
        Loan l = findLoan(lid);
        if (l==null) throw new LoanNotFoundException(lid);
        if (l.getStatus()==LoanStatus.RETURNED||l.getStatus()==LoanStatus.LOST)
            throw new LibraryException("Phiếu đã kết thúc");
        l.returnItem();
    }
    public void markLost(String lid) throws LibraryException {
        Loan l = findLoan(lid);
        if (l==null) throw new LoanNotFoundException(lid);
        if (l.getStatus()==LoanStatus.RETURNED||l.getStatus()==LoanStatus.LOST)
            throw new LibraryException("Phiếu đã kết thúc");
        l.markLost();
    }
    public void renewLoan(String lid) throws LibraryException {
        Loan l = findLoan(lid);
        if (l==null) throw new LoanNotFoundException(lid);
        l.renew();
    }

    // REPORTS
    public List<Loan> getActiveLoans() {
        return loans.stream()
            .filter(l -> l.getStatus()==LoanStatus.BORROWING||l.getStatus()==LoanStatus.OVERDUE)
            .collect(Collectors.toList());
    }
    public List<Loan> getOverdueLoans() {
        return loans.stream().filter(Loan::isOverdue).collect(Collectors.toList());
    }
    public List<Item> getMostBorrowedItems(int n) {
        Map<String,Long> c = loans.stream().collect(Collectors.groupingBy(l -> l.getItem().getId(), Collectors.counting()));
        return c.entrySet().stream()
            .sorted((a,b)->Long.compare(b.getValue(), a.getValue()))
            .limit(n).map(e -> findItem(e.getKey())).filter(Objects::nonNull).collect(Collectors.toList());
    }
    public long countLoansOf(Item it) {
        return loans.stream().filter(l -> l.getItem().getId().equals(it.getId())).count();
    }
    public double getTotalCollectedFees() { return loans.stream().mapToDouble(Loan::getPaidFee).sum(); }
    public double getTotalUnpaidLateFees() { return getActiveLoans().stream().mapToDouble(Loan::getLateFee).sum(); }

    public List<ItemEvent> getItemEvents(PeriodFilter p) {
        LocalDateTime now = TimeUtil.now();
        LocalDateTime start;
        switch (p) {
            case TODAY: start = now.toLocalDate().atStartOfDay(); break;
            case WEEK:  start = now.minusDays(7); break;
            case MONTH: start = now.toLocalDate().withDayOfMonth(1).atStartOfDay(); break;
            case YEAR:  start = now.toLocalDate().withDayOfYear(1).atStartOfDay(); break;
            default:    start = LocalDateTime.MIN;
        }
        return itemEvents.stream().filter(e -> !e.getTimestamp().isBefore(start)).collect(Collectors.toList());
    }
}

// =====================================================================
// 9. PRINT UTIL
// =====================================================================
class PrintUtil {
    static void showAndPrint(Component parent, String title, String content) {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(parent), title, true);
        dlg.setSize(650, 520); dlg.setLocationRelativeTo(parent);
        JTextArea area = new JTextArea(content);
        area.setFont(new Font("Monospaced", Font.PLAIN, 12));
        area.setEditable(false);
        dlg.add(new JScrollPane(area), BorderLayout.CENTER);
        JPanel btns = new JPanel();
        JButton btnPrint = new JButton("In");
        JButton btnClose = new JButton("Đóng");
        btns.add(btnPrint); btns.add(btnClose);
        dlg.add(btns, BorderLayout.SOUTH);
        btnPrint.addActionListener(e -> {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setJobName(title);
            job.setPrintable((g, pf, page) -> {
                if (page > 0) return Printable.NO_SUCH_PAGE;
                Graphics2D g2 = (Graphics2D) g;
                g2.translate(pf.getImageableX(), pf.getImageableY());
                g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
                int y = 14;
                for (String line : content.split("\n")) {
                    g2.drawString(line, 5, y); y += 13;
                    if (y > pf.getImageableHeight()-10) break;
                }
                return Printable.PAGE_EXISTS;
            });
            if (job.printDialog()) {
                try { job.print(); } catch (PrinterException ex) {
                    JOptionPane.showMessageDialog(dlg, "Lỗi in: " + ex.getMessage());
                }
            }
        });
        btnClose.addActionListener(e -> dlg.dispose());
        dlg.setVisible(true);
    }
}

// =====================================================================
// 10. LOGIN FRAME
// =====================================================================
class LoginFrame extends JFrame {
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "thuvien123";

    public LoginFrame() {
        setTitle("Đăng nhập - Quản lý Thư viện");
        setSize(420, 230);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(8,8,8,8); g.fill = GridBagConstraints.HORIZONTAL;
        JLabel lblTitle = new JLabel("QUẢN LÝ THƯ VIỆN - ĐH PHÚ XUÂN", SwingConstants.CENTER);
        lblTitle.setFont(new Font("Arial", Font.BOLD, 14));
        g.gridx=0; g.gridy=0; g.gridwidth=2; add(lblTitle, g);
        JTextField txtUser = new JTextField(15);
        JPasswordField txtPass = new JPasswordField(15);
        g.gridwidth=1;
        g.gridx=0; g.gridy=1; add(new JLabel("Tài khoản:"), g);
        g.gridx=1; add(txtUser, g);
        g.gridx=0; g.gridy=2; add(new JLabel("Mật khẩu:"), g);
        g.gridx=1; add(txtPass, g);
        JButton btnLogin = new JButton("Đăng nhập");
        g.gridx=0; g.gridy=3; g.gridwidth=2; add(btnLogin, g);
        Runnable doLogin = () -> {
            if (ADMIN_USER.equals(txtUser.getText().trim()) && ADMIN_PASS.equals(new String(txtPass.getPassword()))) {
                dispose();
                new MainFrame(new LibraryService()).setVisible(true);
            } else {
                JOptionPane.showMessageDialog(this, "Sai tài khoản hoặc mật khẩu!",
                        "Lỗi đăng nhập", JOptionPane.ERROR_MESSAGE);
                txtPass.setText(""); txtPass.requestFocus();
            }
        };
        btnLogin.addActionListener(e -> doLogin.run());
        txtPass.addActionListener(e -> doLogin.run());
        getRootPane().setDefaultButton(btnLogin);
    }
}

// =====================================================================
// 11. MAIN FRAME
// =====================================================================
class MainFrame extends JFrame {
    public MainFrame(LibraryService service) {
        setTitle("Quản lý Thư viện - ĐH Phú Xuân");
        setSize(1200, 740);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Quản lý Item", new ItemPanel(service));
        tabs.addTab("Quản lý Độc giả", new ReaderPanel(service));
        tabs.addTab("Mượn / Trả", new LoanPanel(service));
        tabs.addTab("Báo cáo & Lịch sử", new ReportPanel(service));
        add(tabs);
    }
}

// =====================================================================
// 12. ITEM PANEL + DIALOG
// =====================================================================
class ItemPanel extends JPanel {
    private final LibraryService service;
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JTextField txtSearch = new JTextField(15);
    private final JComboBox<String> cbTypeFilter = new JComboBox<>(
            new String[]{"Tất cả","Sách","Tạp chí","DVD","Luận văn"});

    public ItemPanel(LibraryService service) {
        this.service = service;
        setLayout(new BorderLayout(5,5));
        setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Tìm:")); top.add(txtSearch);
        top.add(new JLabel("Loại:")); top.add(cbTypeFilter);
        JButton btnSearch = new JButton("Tìm");
        JButton btnRefresh = new JButton("Làm mới");
        JButton btnAdd = new JButton("Thêm");
        JButton btnEdit = new JButton("Sửa");
        JButton btnDelete = new JButton("Xóa");
        top.add(btnSearch); top.add(btnRefresh);
        top.add(Box.createHorizontalStrut(20));
        top.add(btnAdd); top.add(btnEdit); top.add(btnDelete);
        add(top, BorderLayout.NORTH);

        String[] cols = {"Mã","Tên","Loại","NXB","Năm","Lần XB","Giá tiền","Còn/Tổng","Ngày nhập"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r,int c){ return false; }
        };
        table = new JTable(tableModel);
        add(new JScrollPane(table), BorderLayout.CENTER);

        btnSearch.addActionListener(e -> refresh());
        txtSearch.addActionListener(e -> refresh());
        cbTypeFilter.addActionListener(e -> refresh());
        btnRefresh.addActionListener(e -> { txtSearch.setText(""); cbTypeFilter.setSelectedIndex(0); refresh(); });
        btnAdd.addActionListener(e -> openDialog(null));
        btnEdit.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row<0) { msg("Chọn item để sửa"); return; }
            openDialog(service.findItem((String)tableModel.getValueAt(row,0)));
        });
        btnDelete.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row<0) { msg("Chọn item để xóa"); return; }
            String id = (String) tableModel.getValueAt(row,0);
            if (JOptionPane.showConfirmDialog(this, "Xóa item " + id + "?", "Xác nhận",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                try { service.deleteItem(id); refresh(); }
                catch (LibraryException ex) { err(ex.getMessage()); }
            }
        });
        refresh();
    }
    private void refresh() {
        tableModel.setRowCount(0);
        for (Item it : service.searchItems(txtSearch.getText(), (String)cbTypeFilter.getSelectedItem())) {
            tableModel.addRow(new Object[]{
                it.getId(), it.getTitle(), it.getType(), it.getPublisher(),
                it.getPublishYear(), it.getEdition(),
                String.format("%,.0f", it.getPrice()),
                it.getAvailableCopies() + "/" + it.getTotalCopies(),
                TimeUtil.fmt(it.getCreatedAt())
            });
        }
    }
    private void openDialog(Item existing) {
        ItemDialog d = new ItemDialog((JFrame) SwingUtilities.getWindowAncestor(this), service, existing);
        d.setVisible(true);
        if (d.isSaved()) refresh();
    }
    private void msg(String m){ JOptionPane.showMessageDialog(this, m); }
    private void err(String m){ JOptionPane.showMessageDialog(this, m, "Lỗi", JOptionPane.ERROR_MESSAGE); }
}

class ItemDialog extends JDialog {
    private final LibraryService service;
    private final Item existing;
    private boolean saved = false;
    private final JComboBox<String> cbType = new JComboBox<>(new String[]{"Sách","Tạp chí","DVD","Luận văn"});
    private final JTextField txtId = new JTextField(20);
    private final JTextField txtTitle = new JTextField(20);
    private final JTextField txtPublisher = new JTextField(20);
    private final JTextField txtYear = new JTextField(20);
    private final JTextField txtEdition = new JTextField(20);
    private final JTextField txtPrice = new JTextField(20);
    private final JTextField txtCopies = new JTextField(20);
    private JTextField txtAuthor, txtIsbn, txtGenre, txtIssue, txtPeriod, txtDuration, txtDirector, txtStudent, txtSupervisor, txtDegree;
    private final JPanel specificPanel = new JPanel(new GridBagLayout());

    public ItemDialog(JFrame parent, LibraryService service, Item existing) {
        super(parent, existing==null?"Thêm Item":"Sửa Item", true);
        this.service = service; this.existing = existing;
        setLayout(new BorderLayout(5,5));
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4,4,4,4); g.anchor = GridBagConstraints.WEST; g.fill = GridBagConstraints.HORIZONTAL;
        int row=0;
        addRow(form,g,row++,"Loại:", cbType);
        addRow(form,g,row++,"Mã:", txtId);
        addRow(form,g,row++,"Tên (*):", txtTitle);
        addRow(form,g,row++,"NXB:", txtPublisher);
        addRow(form,g,row++,"Năm XB:", txtYear);
        addRow(form,g,row++,"Lần XB:", txtEdition);
        addRow(form,g,row++,"Giá tiền:", txtPrice);
        addRow(form,g,row++,"Tổng bản:", txtCopies);
        g.gridx=0; g.gridy=row; g.gridwidth=2; form.add(specificPanel, g);
        add(form, BorderLayout.NORTH);
        JPanel btns = new JPanel();
        JButton btnSave = new JButton("Lưu");
        JButton btnCancel = new JButton("Hủy");
        btns.add(btnSave); btns.add(btnCancel);
        add(btns, BorderLayout.SOUTH);

        cbType.addActionListener(e -> rebuild());
        btnSave.addActionListener(e -> onSave());
        btnCancel.addActionListener(e -> dispose());

        if (existing!=null) {
            cbType.setSelectedItem(existing.getType()); cbType.setEnabled(false);
            txtId.setText(existing.getId()); txtId.setEditable(false);
            txtTitle.setText(existing.getTitle());
            txtPublisher.setText(existing.getPublisher());
            txtYear.setText(String.valueOf(existing.getPublishYear()));
            txtEdition.setText(String.valueOf(existing.getEdition()));
            txtPrice.setText(String.valueOf((long)existing.getPrice()));
            txtCopies.setText(String.valueOf(existing.getTotalCopies()));
            prefill(existing);
        } else {
            txtId.setEditable(false);
            txtYear.setText(String.valueOf(TimeUtil.today().getYear()));            // FIX C.1
            txtEdition.setText("1"); txtPrice.setText("100000"); txtCopies.setText("1");
        }
        rebuild();
        pack(); setLocationRelativeTo(parent);
        setMinimumSize(new Dimension(500, getHeight()));
    }
    private void addRow(JPanel p, GridBagConstraints g, int row, String label, JComponent c) {
        g.gridx=0; g.gridy=row; g.gridwidth=1; p.add(new JLabel(label), g);
        g.gridx=1; p.add(c, g);
    }
    private void rebuild() {
        specificPanel.removeAll();
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4,4,4,4); g.anchor = GridBagConstraints.WEST; g.fill = GridBagConstraints.HORIZONTAL;
        String type = (String) cbType.getSelectedItem();
        int row=0;
        if ("Sách".equals(type)) {
            if (txtAuthor==null) txtAuthor=new JTextField(20);
            if (txtIsbn==null) txtIsbn=new JTextField(20);
            if (txtGenre==null) txtGenre=new JTextField(20);
            addRow(specificPanel,g,row++,"Tác giả (*):", txtAuthor);
            addRow(specificPanel,g,row++,"ISBN:", txtIsbn);
            addRow(specificPanel,g,row++,"Thể loại:", txtGenre);
        } else if ("Tạp chí".equals(type)) {
            if (txtIssue==null) txtIssue=new JTextField(20);
            if (txtPeriod==null) txtPeriod=new JTextField(20);
            addRow(specificPanel,g,row++,"Số phát hành:", txtIssue);
            addRow(specificPanel,g,row++,"Kỳ (MM/yyyy):", txtPeriod);
        } else if ("DVD".equals(type)) {
            if (txtDuration==null) txtDuration=new JTextField(20);
            if (txtDirector==null) txtDirector=new JTextField(20);
            addRow(specificPanel,g,row++,"Thời lượng (phút):", txtDuration);
            addRow(specificPanel,g,row++,"Đạo diễn:", txtDirector);
        } else {
            if (txtStudent==null) txtStudent=new JTextField(20);
            if (txtSupervisor==null) txtSupervisor=new JTextField(20);
            if (txtDegree==null) txtDegree=new JTextField(20);
            addRow(specificPanel,g,row++,"Học viên (*):", txtStudent);
            addRow(specificPanel,g,row++,"GVHD:", txtSupervisor);
            addRow(specificPanel,g,row++,"Học vị:", txtDegree);
        }
        if (existing==null) {
            Class<? extends Item> cls;
            switch (type) {
                case "Sách": cls=Book.class; break;
                case "Tạp chí": cls=Magazine.class; break;
                case "DVD": cls=DVD.class; break;
                default: cls=Thesis.class;
            }
            txtId.setText(service.suggestNextItemId(cls));
        }
        specificPanel.revalidate(); specificPanel.repaint(); pack();
    }
    private void prefill(Item it) {
        if (it instanceof Book) {
            Book b=(Book)it;
            txtAuthor=new JTextField(b.getAuthor()); txtIsbn=new JTextField(b.getIsbn()); txtGenre=new JTextField(b.getGenre());
        } else if (it instanceof Magazine) {
            Magazine m=(Magazine)it;
            txtIssue=new JTextField(String.valueOf(m.getIssueNumber())); txtPeriod=new JTextField(m.getPeriod());
        } else if (it instanceof DVD) {
            DVD d=(DVD)it;
            txtDuration=new JTextField(String.valueOf(d.getDurationMinutes())); txtDirector=new JTextField(d.getDirector());
        } else if (it instanceof Thesis) {
            Thesis t=(Thesis)it;
            txtStudent=new JTextField(t.getStudentName()); txtSupervisor=new JTextField(t.getSupervisor()); txtDegree=new JTextField(t.getDegree());
        }
    }
    private int pInt(String s, String f) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { throw new InvalidInputException(f+" phải là số nguyên"); }
    }
    private double pDouble(String s, String f) {
        try { return Double.parseDouble(s.trim()); }
        catch (NumberFormatException e) { throw new InvalidInputException(f+" phải là số"); }
    }
    private void onSave() {
        try {
            String id = txtId.getText().trim();
            String type = (String) cbType.getSelectedItem();
            Item item;
            if ("Sách".equals(type))
                item = new Book(id, txtTitle.getText().trim(), txtPublisher.getText().trim(),
                        pInt(txtYear.getText(),"Năm XB"), pInt(txtEdition.getText(),"Lần XB"),
                        pDouble(txtPrice.getText(),"Giá tiền"), pInt(txtCopies.getText(),"Tổng bản"),
                        txtAuthor.getText().trim(), txtIsbn.getText().trim(), txtGenre.getText().trim());
            else if ("Tạp chí".equals(type))
                item = new Magazine(id, txtTitle.getText().trim(), txtPublisher.getText().trim(),
                        pInt(txtYear.getText(),"Năm XB"), pInt(txtEdition.getText(),"Lần XB"),
                        pDouble(txtPrice.getText(),"Giá tiền"), pInt(txtCopies.getText(),"Tổng bản"),
                        pInt(txtIssue.getText(),"Số phát hành"), txtPeriod.getText().trim());
            else if ("DVD".equals(type))
                item = new DVD(id, txtTitle.getText().trim(), txtPublisher.getText().trim(),
                        pInt(txtYear.getText(),"Năm XB"), pInt(txtEdition.getText(),"Lần XB"),
                        pDouble(txtPrice.getText(),"Giá tiền"), pInt(txtCopies.getText(),"Tổng bản"),
                        pInt(txtDuration.getText(),"Thời lượng"), txtDirector.getText().trim());
            else
                item = new Thesis(id, txtTitle.getText().trim(), txtPublisher.getText().trim(),
                        pInt(txtYear.getText(),"Năm XB"), pInt(txtEdition.getText(),"Lần XB"),
                        pDouble(txtPrice.getText(),"Giá tiền"), pInt(txtCopies.getText(),"Tổng bản"),
                        txtStudent.getText().trim(), txtSupervisor.getText().trim(), txtDegree.getText().trim());
            if (existing==null) service.addItem(item);
            else service.updateItem(item);
            saved=true; dispose();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Lỗi nhập liệu", JOptionPane.ERROR_MESSAGE);
        } catch (LibraryException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
    public boolean isSaved(){ return saved; }
}

// =====================================================================
// 13. READER PANEL + DIALOG
// =====================================================================
class ReaderPanel extends JPanel {
    private final LibraryService service;
    private final DefaultTableModel tableModel;
    private final JTable table;

    public ReaderPanel(LibraryService service) {
        this.service = service;
        setLayout(new BorderLayout(5,5));
        setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnAdd = new JButton("Thêm");
        JButton btnEdit = new JButton("Sửa");
        JButton btnDelete = new JButton("Xóa");
        JButton btnRenew = new JButton("Gia hạn");
        JButton btnInvoice = new JButton("In hoá đơn");
        JButton btnRefresh = new JButton("Làm mới");
        top.add(btnAdd); top.add(btnEdit); top.add(btnDelete);
        top.add(btnRenew); top.add(btnInvoice); top.add(btnRefresh);
        add(top, BorderLayout.NORTH);
        String[] cols = {"Mã","Họ tên","Loại thẻ","Có thẻ cứng","Hạn thẻ","Đang mượn","Tổng đã trả"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r,int c){ return false; }
        };
        table = new JTable(tableModel);
        add(new JScrollPane(table), BorderLayout.CENTER);

        btnAdd.addActionListener(e -> openDialog(null));
        btnEdit.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row<0) { msg("Chọn độc giả để sửa"); return; }
            openDialog(service.findReader((String)tableModel.getValueAt(row,0)));
        });
        btnDelete.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row<0) { msg("Chọn độc giả để xóa"); return; }
            String id = (String) tableModel.getValueAt(row,0);
            if (JOptionPane.showConfirmDialog(this, "Xóa độc giả " + id + "?", "Xác nhận",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                try { service.deleteReader(id); refresh(); }
                catch (LibraryException ex) { err(ex.getMessage()); }
            }
        });
        btnRenew.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row<0) { msg("Chọn độc giả để gia hạn"); return; }
            Reader r = service.findReader((String)tableModel.getValueAt(row,0));
            if (r==null) return;
            if (r.getCardType()==CardType.NONE) { msg("Độc giả chưa có thẻ. Hãy Sửa để đăng ký thẻ."); return; }
            RenewDialog d = new RenewDialog((JFrame) SwingUtilities.getWindowAncestor(this), service, r);
            d.setVisible(true);
            refresh();
        });
        btnInvoice.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row<0) { msg("Chọn độc giả để in hoá đơn"); return; }
            Reader r = service.findReader((String)tableModel.getValueAt(row,0));
            if (r==null) return;
            PrintUtil.showAndPrint(this, "Hoá đơn - " + r.getId(), buildInvoice(r));
        });
        btnRefresh.addActionListener(e -> refresh());
        refresh();
    }
    private void refresh() {
        tableModel.setRowCount(0);
        for (Reader r : service.getAllReaders()) {
            String end = r.getSubscriptionEnd()==null ? "-" : TimeUtil.fmt(r.getSubscriptionEnd());
            tableModel.addRow(new Object[]{
                r.getId(), r.getFullName(), r.getCardType().getLabel(),
                r.hasPhysicalCard() ? "Có" : "Chưa",
                end,
                r.getActiveLoanCount() + "/" + r.getMaxLoans(),
                String.format("%,.0f", r.getTotalPaid())
            });
        }
    }
    private String buildInvoice(Reader r) {
        StringBuilder sb = new StringBuilder();
        sb.append("========== HOÁ ĐƠN THƯ VIỆN ==========\n");
        sb.append("Thư viện ĐH Phú Xuân\n");
        sb.append("Ngày lập: ").append(TimeUtil.fmt(TimeUtil.now())).append("\n\n");
        sb.append("Mã độc giả: ").append(r.getId()).append("\n");
        sb.append("Họ tên    : ").append(r.getFullName()).append("\n");
        sb.append("Điện thoại: ").append(r.getPhone()==null?"":r.getPhone()).append("\n");
        sb.append("Email     : ").append(r.getEmail()==null?"":r.getEmail()).append("\n");
        sb.append("Địa chỉ   : ").append(r.getAddress()==null?"":r.getAddress()).append("\n");
        sb.append("Loại thẻ  : ").append(r.getCardType().getLabel()).append("\n");
        sb.append("Thẻ cứng  : ").append(r.hasPhysicalCard()?"Đã cấp":"Chưa cấp").append("\n");
        if (r.getSubscriptionStart()!=null)
            sb.append("Hiệu lực  : ").append(TimeUtil.fmt(r.getSubscriptionStart()))
              .append(" → ").append(TimeUtil.fmt(r.getSubscriptionEnd())).append("\n");
        sb.append("\n---------- CÁC KHOẢN THU ----------\n");
        if (r.getPayments().isEmpty()) sb.append("(Chưa có giao dịch)\n");
        else {
            double sum=0;
            for (PaymentRecord p : r.getPayments()) {
                sb.append(TimeUtil.fmt(p.getTimestamp())).append("  ")
                  .append(String.format("%,12.0f đ", p.getAmount()))
                  .append("  ").append(p.getDescription()).append("\n");
                sum += p.getAmount();
            }
            sb.append("\nTỔNG ĐÃ THU: ").append(String.format("%,.0f đ", sum)).append("\n");
        }
        sb.append("\nCảm ơn quý độc giả!\n");
        return sb.toString();
    }
    private void openDialog(Reader existing) {
        ReaderDialog d = new ReaderDialog((JFrame) SwingUtilities.getWindowAncestor(this), service, existing);
        d.setVisible(true);
        if (d.isSaved()) {
            refresh();
            if (d.getInvoiceText()!=null)
                PrintUtil.showAndPrint(this, "Hoá đơn đăng ký", d.getInvoiceText());
        }
    }
    private void msg(String m){ JOptionPane.showMessageDialog(this, m); }
    private void err(String m){ JOptionPane.showMessageDialog(this, m, "Lỗi", JOptionPane.ERROR_MESSAGE); }
}

class ReaderDialog extends JDialog {
    private final LibraryService service;
    private final Reader existing;
    private boolean saved = false;
    private String invoiceText;
    private final JTextField txtId = new JTextField(20);
    private final JTextField txtName = new JTextField(20);
    private final JComboBox<Integer> cbDay = new JComboBox<>();
    private final JComboBox<Integer> cbMonth = new JComboBox<>();
    private final JTextField txtYear = new JTextField(6);
    private final JTextField txtPhone = new JTextField(20);
    private final JTextField txtEmail = new JTextField(20);
    private final JTextField txtAddress = new JTextField(20);
    private final JComboBox<CardType> cbType = new JComboBox<>(CardType.values());
    private final JCheckBox chkHardCard = new JCheckBox("Cấp thẻ cứng (20.000đ)", true);
    private final JTextField txtMonths = new JTextField(20);
    private final JLabel lblFee = new JLabel("-");
    private final JLabel lblExpiry = new JLabel("-");

    public ReaderDialog(JFrame parent, LibraryService service, Reader existing) {
        super(parent, existing==null?"Thêm Độc giả":"Sửa Độc giả", true);
        this.service = service; this.existing = existing;
        setLayout(new BorderLayout(5,5));
        for (int i = 1; i <= 31; i++) cbDay.addItem(i);
        for (int i = 1; i <= 12; i++) cbMonth.addItem(i);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4,4,4,4); g.anchor = GridBagConstraints.WEST; g.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;
        addRow(form,g,row++,"Mã:", txtId);
        addRow(form,g,row++,"Họ tên (*):", txtName);
        JPanel dobPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        dobPanel.add(cbDay); dobPanel.add(new JLabel("/")); dobPanel.add(cbMonth);
        dobPanel.add(new JLabel("/")); dobPanel.add(txtYear);
        addRow(form,g,row++,"Ngày sinh (N/T/N):", dobPanel);
        addRow(form,g,row++,"SĐT (*):", txtPhone);
        addRow(form,g,row++,"Email:", txtEmail);
        addRow(form,g,row++,"Địa chỉ (*):", txtAddress);
        addRow(form,g,row++,"Loại thẻ:", cbType);
        addRow(form,g,row++,"Số tháng đăng ký:", txtMonths);
        addRow(form,g,row++,"", chkHardCard);
        lblFee.setForeground(new Color(0,120,0));
        lblFee.setFont(lblFee.getFont().deriveFont(Font.BOLD));
        addRow(form,g,row++,"Phí cần thu:", lblFee);
        lblExpiry.setForeground(Color.GRAY);
        addRow(form,g,row++,"Hết hạn (dự kiến):", lblExpiry);
        add(form, BorderLayout.NORTH);

        JPanel btns = new JPanel();
        JButton btnSave = new JButton("Lưu");
        JButton btnCancel = new JButton("Hủy");
        btns.add(btnSave); btns.add(btnCancel);
        add(btns, BorderLayout.SOUTH);

        Runnable updateFee = () -> {
            try {
                CardType t = (CardType) cbType.getSelectedItem();
                if (t == CardType.NONE) { lblFee.setText("0 đ"); lblExpiry.setText("-"); return; }
                int months = txtMonths.getText().trim().isEmpty()?0 : Integer.parseInt(txtMonths.getText().trim());
                boolean wants = existing!=null ? false : chkHardCard.isSelected();
                double fee = existing==null
                        ? service.calcNewRegistrationFee(t, months, wants)
                        : service.calcRenewalFee(t, months);
                lblFee.setText(String.format("%,.0f đ", fee));
                LocalDate base = (existing!=null && existing.getSubscriptionEnd()!=null
                                    && existing.getSubscriptionEnd().isAfter(TimeUtil.today()))   // FIX C.1
                                ? existing.getSubscriptionEnd() : TimeUtil.today();
                lblExpiry.setText(TimeUtil.fmt(base.plusMonths(months)));
            } catch (Exception ex) { lblFee.setText("?"); lblExpiry.setText("?"); }
        };
        cbType.addActionListener(e -> updateFee.run());
        txtMonths.addCaretListener(e -> updateFee.run());
        chkHardCard.addActionListener(e -> updateFee.run());
        btnSave.addActionListener(e -> onSave());
        btnCancel.addActionListener(e -> dispose());

        if (existing!=null) {
            txtId.setText(existing.getId()); txtId.setEditable(false);
            txtName.setText(existing.getFullName());
            if (existing.getDateOfBirth() != null) {
                cbDay.setSelectedItem(existing.getDateOfBirth().getDayOfMonth());
                cbMonth.setSelectedItem(existing.getDateOfBirth().getMonthValue());
                txtYear.setText(String.valueOf(existing.getDateOfBirth().getYear()));
            }
            txtPhone.setText(existing.getPhone()==null?"":existing.getPhone());
            txtEmail.setText(existing.getEmail()==null?"":existing.getEmail());
            txtAddress.setText(existing.getAddress()==null?"":existing.getAddress());
            cbType.setSelectedItem(existing.getCardType());
            // FIX C.2 - KHÔNG khoá cbType, nhưng xử lý đổi đúng trong onSave
            chkHardCard.setSelected(existing.hasPhysicalCard());
            chkHardCard.setEnabled(false); // đã có thẻ cứng thì không đổi qua checkbox
            txtMonths.setText("12");
        } else {
            txtId.setText(service.suggestNextReaderId()); txtId.setEditable(false);
            cbType.setSelectedItem(CardType.BORROW_ADULT);
            txtMonths.setText("12");
        }
        updateFee.run();
        pack(); setLocationRelativeTo(parent);
        setMinimumSize(new Dimension(480, getHeight()));
    }
    private void addRow(JPanel p, GridBagConstraints g, int row, String l, JComponent c) {
        g.gridx=0; g.gridy=row; g.gridwidth=1; p.add(new JLabel(l), g);
        g.gridx=1; p.add(c, g);
    }
    private void onSave() {
        try {
            String id = txtId.getText().trim();
            String name = txtName.getText().trim();
            String phone = txtPhone.getText().trim();
            String address = txtAddress.getText().trim();
            if (name.isEmpty()) throw new InvalidInputException("Họ tên không được để trống");
            if (phone.isEmpty()) throw new InvalidInputException("SĐT không được để trống");
            if (address.isEmpty()) throw new InvalidInputException("Địa chỉ không được để trống");

            CardType type = (CardType) cbType.getSelectedItem();
            int months = 0;
            if (type != CardType.NONE) {
                if (txtMonths.getText().trim().isEmpty())
                    throw new InvalidInputException("Số tháng không được rỗng");
                months = Integer.parseInt(txtMonths.getText().trim());
            }

            LocalDate dob = null;
            if (!txtYear.getText().trim().isEmpty()) {
                int y = Integer.parseInt(txtYear.getText().trim());
                if (y > TimeUtil.today().getYear()) throw new InvalidInputException("Năm sinh vượt quá năm hiện tại");  // FIX C.1
                if (y < 1900) throw new InvalidInputException("Năm sinh không hợp lệ");
                int m = (int) cbMonth.getSelectedItem();
                int d = (int) cbDay.getSelectedItem();
                try { dob = LocalDate.of(y, m, d); }
                catch (Exception e) { throw new InvalidInputException("Ngày sinh không hợp lệ (VD: 30/02)"); }
            }

            if (existing == null) {
                Reader r = new Reader(id, name, type);
                r.setDateOfBirth(dob); r.setPhone(phone);
                r.setEmail(txtEmail.getText().trim()); r.setAddress(address);
                boolean wantsHardCard = type != CardType.NONE && chkHardCard.isSelected();
                r.setHasPhysicalCard(wantsHardCard);
                double fee = 0;
                if (type != CardType.NONE && months > 0) {
                    fee = service.calcNewRegistrationFee(type, months, wantsHardCard);
                    r.subscribe(months, fee);
                }
                service.addReader(r);
                invoiceText = buildNewInvoice(r, months, wantsHardCard, fee);
            } else {
                // FIX C.2 - đổi loại thẻ trước
                if (type != CardType.NONE && type != existing.getCardType()) {
                    existing.changeCardType(type);
                }
                Reader upd = new Reader(id, name, existing.getCardType());
                upd.setDateOfBirth(dob); upd.setPhone(phone);
                upd.setEmail(txtEmail.getText().trim()); upd.setAddress(address);
                upd.setHasPhysicalCard(existing.hasPhysicalCard());
                service.updateReader(upd);
                if (type != CardType.NONE && months > 0) {
                    double fee = service.calcRenewalFee(type, months);
                    existing.subscribe(months, fee);
                    invoiceText = buildNewInvoice(existing, months, false, fee);
                }
            }
            saved = true; dispose();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Lỗi nhập liệu", JOptionPane.ERROR_MESSAGE);
        } catch (LibraryException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
    private String buildNewInvoice(Reader r, int months, boolean showCardFee, double fee) {
        StringBuilder sb = new StringBuilder();
        sb.append("========== HOÁ ĐƠN ĐĂNG KÝ ==========\n");
        sb.append("Thư viện ĐH Phú Xuân\n");
        sb.append("Ngày lập: ").append(TimeUtil.fmt(TimeUtil.now())).append("\n\n");
        sb.append("Mã độc giả: ").append(r.getId()).append("\n");
        sb.append("Họ tên    : ").append(r.getFullName()).append("\n");
        sb.append("Loại thẻ  : ").append(r.getCardType().getLabel()).append("\n");
        sb.append("Số tháng  : ").append(months).append("\n");
        if (r.getSubscriptionStart() != null) {
            sb.append("Hiệu lực  : ").append(TimeUtil.fmt(r.getSubscriptionStart()))
              .append(" → ").append(TimeUtil.fmt(r.getSubscriptionEnd())).append("\n\n");
        }
        sb.append("---------- CHI TIẾT ----------\n");
        double cardFee = showCardFee ? LibraryService.HARD_CARD_FEE : 0;
        double subFee = fee - cardFee;
        if (showCardFee) sb.append(String.format("Phí thẻ cứng    : %,.0f đ\n", cardFee));
        sb.append(String.format("Gói đăng ký     : %,.0f đ\n", subFee));
        sb.append("\nTỔNG CỘNG: ").append(String.format("%,.0f đ", fee)).append("\n");
        sb.append("\nCảm ơn quý độc giả!\n");
        return sb.toString();
    }
    public boolean isSaved(){ return saved; }
    public String getInvoiceText(){ return invoiceText; }
}

// =====================================================================
// 14. RENEW DIALOG
// =====================================================================
class RenewDialog extends JDialog {
    private final JTextField txtMonths = new JTextField("12", 10);
    private final JLabel lblFee = new JLabel("-");
    private final JLabel lblNewEnd = new JLabel("-");

    public RenewDialog(JFrame parent, LibraryService service, Reader r) {
        super(parent, "Gia hạn thẻ - " + r.getId(), true);
        setLayout(new BorderLayout(5,5));
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4,4,4,4); g.anchor = GridBagConstraints.WEST;
        int row=0;
        addRow(form,g,row++, "Độc giả:", new JLabel(r.getFullName()));
        addRow(form,g,row++, "Loại thẻ:", new JLabel(r.getCardType().getLabel()));
        addRow(form,g,row++, "Hạn hiện tại:",
                new JLabel(r.getSubscriptionEnd()==null?"-":TimeUtil.fmt(r.getSubscriptionEnd())));
        addRow(form,g,row++, "Gia hạn (tháng):", txtMonths);
        lblFee.setForeground(new Color(0,120,0));
        lblFee.setFont(lblFee.getFont().deriveFont(Font.BOLD));
        addRow(form,g,row++, "Phí:", lblFee);
        addRow(form,g,row++, "Hạn mới:", lblNewEnd);
        add(form, BorderLayout.NORTH);

        JPanel btns = new JPanel();
        JButton btnOk = new JButton("Gia hạn");
        JButton btnCancel = new JButton("Hủy");
        btns.add(btnOk); btns.add(btnCancel);
        add(btns, BorderLayout.SOUTH);

        Runnable upd = () -> {
            try {
                int m = Integer.parseInt(txtMonths.getText().trim());
                double fee = service.calcRenewalFee(r.getCardType(), m);
                lblFee.setText(String.format("%,.0f đ", fee));
                LocalDate base = (r.getSubscriptionEnd()!=null && r.getSubscriptionEnd().isAfter(TimeUtil.today()))  // FIX C.1
                        ? r.getSubscriptionEnd() : TimeUtil.today();
                lblNewEnd.setText(TimeUtil.fmt(base.plusMonths(m)));
            } catch (Exception ex) { lblFee.setText("?"); lblNewEnd.setText("?"); }
        };
        txtMonths.addCaretListener(e -> upd.run());
        upd.run();

        btnOk.addActionListener(e -> {
            try {
                int m = Integer.parseInt(txtMonths.getText().trim());
                if (m <= 0) throw new InvalidInputException("Số tháng > 0");
                double fee = service.calcRenewalFee(r.getCardType(), m);
                r.subscribe(m, fee);
                StringBuilder sb = new StringBuilder();
                sb.append("========== HOÁ ĐƠN GIA HẠN ==========\n");
                sb.append("Thư viện ĐH Phú Xuân\n");
                sb.append("Ngày lập: ").append(TimeUtil.fmt(TimeUtil.now())).append("\n\n");
                sb.append("Mã độc giả: ").append(r.getId()).append("\n");
                sb.append("Họ tên    : ").append(r.getFullName()).append("\n");
                sb.append("Gói       : ").append(r.getCardType().getLabel()).append("\n");
                sb.append("Số tháng  : ").append(m).append("\n");
                sb.append("Hạn mới   : ").append(TimeUtil.fmt(r.getSubscriptionEnd())).append("\n");
                sb.append("\nSỐ TIỀN: ").append(String.format("%,.0f đ", fee)).append("\n");
                PrintUtil.showAndPrint(this, "Hoá đơn gia hạn - " + r.getId(), sb.toString());
                dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });
        btnCancel.addActionListener(e -> dispose());
        pack(); setLocationRelativeTo(parent);
    }
    private void addRow(JPanel p, GridBagConstraints g, int row, String l, JComponent c) {
        g.gridx=0; g.gridy=row; g.gridwidth=1; p.add(new JLabel(l), g);
        g.gridx=1; p.add(c, g);
    }
}

// =====================================================================
// 15. LOAN PANEL
// =====================================================================
class LoanPanel extends JPanel {
    private final LibraryService service;
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JTextField txtReaderId = new JTextField(8);
    private final JTextField txtItemId = new JTextField(8);
    private final JComboBox<Integer> cbDueDay = new JComboBox<>();
    private final JComboBox<Integer> cbDueMonth = new JComboBox<>();
    private final JTextField txtDueYear = new JTextField(4);

    public LoanPanel(LibraryService service) {
        this.service = service;
        setLayout(new BorderLayout(5,5));
        setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        for (int i = 1; i <= 31; i++) cbDueDay.addItem(i);
        for (int i = 1; i <= 12; i++) cbDueMonth.addItem(i);
        LocalDate defaultDue = TimeUtil.today().plusDays(30);                       // FIX C.1
        cbDueDay.setSelectedItem(defaultDue.getDayOfMonth());
        cbDueMonth.setSelectedItem(defaultDue.getMonthValue());
        txtDueYear.setText(String.valueOf(defaultDue.getYear()));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Mã ĐG:")); top.add(txtReaderId);
        JButton btnFindReader = new JButton("Tìm ĐG");
        top.add(btnFindReader);
        top.add(new JLabel("Mã item:")); top.add(txtItemId);
        JButton btnFindItem = new JButton("Tìm Item");
        top.add(btnFindItem);
        JPanel duePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        duePanel.add(new JLabel("Hạn trả (N/T/N):"));
        duePanel.add(cbDueDay); duePanel.add(new JLabel("/"));
        duePanel.add(cbDueMonth); duePanel.add(new JLabel("/"));
        duePanel.add(txtDueYear);
        top.add(duePanel);
        JButton btnBorrow = new JButton("Mượn");
        top.add(btnBorrow);
        JButton btnRefresh = new JButton("Làm mới");
        top.add(btnRefresh);
        add(top, BorderLayout.NORTH);

        String[] cols = {"Mã phiếu","Độc giả","Item","Ngày mượn","Hạn trả","Trạng thái","Phạt"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r,int c){ return false; }
        };
        table = new JTable(tableModel);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v,
                    boolean s, boolean f, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, v, s, f, row, col);
                if (!s) {
                    String st = (String) t.getValueAt(row, 5);
                    if (st != null && st.contains("Quá hạn")) c.setBackground(new Color(255,220,220));
                    else if (st != null && st.contains("Mất")) c.setBackground(new Color(255,190,190));
                    else if (st != null && st.contains("Đã trả")) c.setBackground(new Color(230,255,230));
                    else c.setBackground(Color.WHITE);
                }
                return c;
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnReturn = new JButton("Trả");
        JButton btnLost = new JButton("Báo mất");
        JButton btnRenew = new JButton("Gia hạn");
        btns.add(btnReturn); btns.add(btnLost); btns.add(btnRenew);
        add(btns, BorderLayout.SOUTH);

        btnFindReader.addActionListener(e -> {
            Reader r = showReaderSearchDialog();
            if (r != null) txtReaderId.setText(r.getId());
        });
        // FIX C.3 - khi chọn item, tự cập nhật hạn trả theo loại
        btnFindItem.addActionListener(e -> {
            Item it = showItemSearchDialog();
            if (it != null) {
                txtItemId.setText(it.getId());
                if (it.getMaxLoanDays() > 0) {
                    LocalDate def = TimeUtil.today().plusDays(it.getMaxLoanDays()); // FIX C.1
                    cbDueDay.setSelectedItem(def.getDayOfMonth());
                    cbDueMonth.setSelectedItem(def.getMonthValue());
                    txtDueYear.setText(String.valueOf(def.getYear()));
                }
            }
        });
        btnBorrow.addActionListener(e -> {
            try {
                int d = (int) cbDueDay.getSelectedItem();
                int m = (int) cbDueMonth.getSelectedItem();
                int y = Integer.parseInt(txtDueYear.getText().trim());
                LocalDate dueDate;
                try { dueDate = LocalDate.of(y, m, d); }
                catch (Exception ex) { throw new InvalidInputException("Hạn trả không hợp lệ (VD: 30/02)"); }
                Loan l = service.borrow(txtReaderId.getText().trim(), txtItemId.getText().trim(), dueDate);
                JOptionPane.showMessageDialog(this, "Mượn thành công!\nPhiếu: " + l.getLoanId()
                        + "\nHạn trả: " + TimeUtil.fmt(l.getDueDate()));
                txtReaderId.setText(""); txtItemId.setText("");
                LocalDate resetDue = TimeUtil.today().plusDays(30);                 // FIX C.1
                cbDueDay.setSelectedItem(resetDue.getDayOfMonth());
                cbDueMonth.setSelectedItem(resetDue.getMonthValue());
                txtDueYear.setText(String.valueOf(resetDue.getYear()));
                refresh();
            } catch (IllegalArgumentException ex) { err(ex.getMessage()); }
            catch (LibraryException ex) { err(ex.getMessage()); }
        });
        btnRefresh.addActionListener(e -> refresh());
        btnReturn.addActionListener(e -> {
            Loan l = getSel(); if (l==null) return;
            double fee = l.getLateFee();
            String msg = "Trả phiếu " + l.getLoanId() + "?";
            if (fee > 0) msg += "\nPhí phạt: " + String.format("%,.0f đ", fee);
            if (JOptionPane.showConfirmDialog(this, msg, "Xác nhận",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                try {
                    service.returnLoan(l.getLoanId()); refresh();
                    if (fee > 0) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("========== HOÁ ĐƠN TRẢ SÁCH ==========\n");
                        sb.append("Thư viện ĐH Phú Xuân\n");
                        sb.append("Ngày lập: ").append(TimeUtil.fmt(TimeUtil.now())).append("\n\n");
                        sb.append("Phiếu     : ").append(l.getLoanId()).append("\n");
                        sb.append("Độc giả   : ").append(l.getReader().getFullName()).append("\n");
                        sb.append("Item      : ").append(l.getItem().getTitle()).append("\n");
                        sb.append("Hạn trả   : ").append(TimeUtil.fmt(l.getDueDate())).append("\n");
                        sb.append("Ngày trả  : ").append(TimeUtil.fmt(l.getReturnDate())).append("\n");
                        sb.append("Trễ       : ").append(l.getDaysLate()).append(" ngày\n");
                        sb.append("\nPHÍ PHẠT: ").append(String.format("%,.0f đ", fee)).append("\n");
                        PrintUtil.showAndPrint(this, "Hoá đơn trả - " + l.getLoanId(), sb.toString());
                    }
                } catch (LibraryException ex) { err(ex.getMessage()); }
            }
        });
        btnLost.addActionListener(e -> {
            Loan l = getSel(); if (l==null) return;
            if (JOptionPane.showConfirmDialog(this, "Báo mất phiếu " + l.getLoanId()
                    + "?\nPhí đền (x2 giá): " + String.format("%,.0f đ", l.getLateFee()),
                    "Xác nhận", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                try { service.markLost(l.getLoanId()); refresh(); }
                catch (LibraryException ex) { err(ex.getMessage()); }
            }
        });
        btnRenew.addActionListener(e -> {
            Loan l = getSel(); if (l==null) return;
            try {
                service.renewLoan(l.getLoanId());
                JOptionPane.showMessageDialog(this, "Gia hạn thành công. Hạn mới: "
                        + TimeUtil.fmt(l.getDueDate()));
                refresh();
            } catch (LibraryException ex) { err(ex.getMessage()); }
        });
        refresh();
    }
    private Reader showReaderSearchDialog() {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Tìm Độc giả", true);
        dlg.setSize(600, 400); dlg.setLocationRelativeTo(this);
        JTextField txtSearch = new JTextField(20);
        DefaultTableModel model = new DefaultTableModel(new String[]{"Mã", "Họ tên", "SĐT"}, 0);
        JTable t = new JTable(model);
        Runnable search = () -> {
            model.setRowCount(0);
            String kw = txtSearch.getText().trim().toLowerCase();
            for (Reader r : service.getAllReaders())
                if (r.getId().toLowerCase().contains(kw) || r.getFullName().toLowerCase().contains(kw))
                    model.addRow(new Object[]{r.getId(), r.getFullName(), r.getPhone()});
        };
        txtSearch.addCaretListener(e -> search.run());
        JPanel pnlTop = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pnlTop.add(new JLabel("Tìm theo Mã hoặc Tên:")); pnlTop.add(txtSearch);
        dlg.add(pnlTop, BorderLayout.NORTH);
        dlg.add(new JScrollPane(t), BorderLayout.CENTER);
        JButton btnOk = new JButton("Chọn");
        JPanel pnlBottom = new JPanel(); pnlBottom.add(btnOk);
        dlg.add(pnlBottom, BorderLayout.SOUTH);
        final Reader[] result = {null};
        btnOk.addActionListener(e -> {
            int row = t.getSelectedRow();
            if (row >= 0) { result[0] = service.findReader((String) model.getValueAt(row, 0)); dlg.dispose(); }
            else JOptionPane.showMessageDialog(dlg, "Vui lòng chọn một độc giả");
        });
        search.run(); dlg.setVisible(true); return result[0];
    }
    private Item showItemSearchDialog() {
        JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Tìm Item", true);
        dlg.setSize(600, 400); dlg.setLocationRelativeTo(this);
        JTextField txtSearch = new JTextField(20);
        DefaultTableModel model = new DefaultTableModel(new String[]{"Mã", "Tên", "Loại", "Còn/Tổng"}, 0);
        JTable t = new JTable(model);
        Runnable search = () -> {
            model.setRowCount(0);
            String kw = txtSearch.getText().trim().toLowerCase();
            for (Item it : service.getAllItems())
                if (it.getId().toLowerCase().contains(kw) || it.getTitle().toLowerCase().contains(kw))
                    model.addRow(new Object[]{it.getId(), it.getTitle(), it.getType(),
                            it.getAvailableCopies() + "/" + it.getTotalCopies()});
        };
        txtSearch.addCaretListener(e -> search.run());
        JPanel pnlTop = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pnlTop.add(new JLabel("Tìm theo Mã hoặc Tên:")); pnlTop.add(txtSearch);
        dlg.add(pnlTop, BorderLayout.NORTH);
        dlg.add(new JScrollPane(t), BorderLayout.CENTER);
        JButton btnOk = new JButton("Chọn");
        JPanel pnlBottom = new JPanel(); pnlBottom.add(btnOk);
        dlg.add(pnlBottom, BorderLayout.SOUTH);
        final Item[] result = {null};
        btnOk.addActionListener(e -> {
            int row = t.getSelectedRow();
            if (row >= 0) { result[0] = service.findItem((String) model.getValueAt(row, 0)); dlg.dispose(); }
            else JOptionPane.showMessageDialog(dlg, "Vui lòng chọn một item");
        });
        search.run(); dlg.setVisible(true); return result[0];
    }
    private Loan getSel() {
        int row = table.getSelectedRow();
        if (row<0) { JOptionPane.showMessageDialog(this, "Chọn phiếu"); return null; }
        return service.findLoan((String)tableModel.getValueAt(row, 0));
    }
    private void err(String m){ JOptionPane.showMessageDialog(this, m, "Lỗi", JOptionPane.ERROR_MESSAGE); }
    private void refresh() {
        tableModel.setRowCount(0);
        for (Loan l : service.getAllLoans()) {
            String fee = (l.getStatus()==LoanStatus.RETURNED||l.getStatus()==LoanStatus.LOST)
                    ? String.format("%,.0f đ", l.getPaidFee())
                    : String.format("%,.0f đ (dự kiến)", l.getLateFee());
            tableModel.addRow(new Object[]{
                l.getLoanId(),
                l.getReader().getId() + " - " + l.getReader().getFullName(),
                l.getItem().getId() + " - " + l.getItem().getTitle(),
                TimeUtil.fmt(l.getBorrowDate()), TimeUtil.fmt(l.getDueDate()),
                l.getDisplayStatus().toString(), fee
            });
        }
    }
}

// =====================================================================
// 16. REPORT PANEL
// =====================================================================
class ReportPanel extends JPanel {
    private final LibraryService service;
    private final JTextArea area = new JTextArea();
    private String lastTitle = "Báo cáo";
    private String lastContent = "";

    public ReportPanel(LibraryService service) {
        this.service = service;
        setLayout(new BorderLayout(5,5));
        setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnActive = new JButton("Đang mượn");
        JButton btnOverdue = new JButton("Quá hạn");
        JButton btnTop = new JButton("Top mượn nhiều");
        JButton btnFees = new JButton("Tổng phí phạt");
        JButton btnHistory = new JButton("Lịch sử Item");
        JComboBox<PeriodFilter> cbPeriod = new JComboBox<>(PeriodFilter.values());
        JButton btnPrint = new JButton("In báo cáo");
        top.add(btnActive); top.add(btnOverdue); top.add(btnTop);
        top.add(btnFees); top.add(btnHistory);
        top.add(new JLabel("Kỳ: "));
        top.add(cbPeriod); top.add(btnPrint);
        add(top, BorderLayout.NORTH);
        area.setEditable(false);
        area.setFont(new Font("Monospaced", Font.PLAIN, 12));
        add(new JScrollPane(area), BorderLayout.CENTER);
        btnActive.addActionListener(e -> showActive());
        btnOverdue.addActionListener(e -> showOverdue());
        btnTop.addActionListener(e -> showTop());
        btnFees.addActionListener(e -> showFees());
        btnHistory.addActionListener(e -> showHistory((PeriodFilter) cbPeriod.getSelectedItem()));
        btnPrint.addActionListener(e -> PrintUtil.showAndPrint(this, lastTitle, lastContent));
        showActive();
    }
    private void setContent(String title, String content) {
        lastTitle = title; lastContent = content; area.setText(content);
    }
    private void showActive() {
        StringBuilder sb = new StringBuilder("=== SÁCH ĐANG MƯỢN ===\n");
        List<Loan> list = service.getActiveLoans();
        sb.append("Tổng: ").append(list.size()).append(" phiếu\n\n");
        for (Loan l : list) sb.append(String.format(
                "%s | %s | %s | Hạn: %s | %s\n", l.getLoanId(),
                l.getReader().getFullName(), l.getItem().getTitle(),
                TimeUtil.fmt(l.getDueDate()),
                l.isOverdue() ? "QUÁ HẠN " + l.getDaysLate() + " ngày" : "OK"));
        setContent("Báo cáo đang mượn", sb.toString());
    }
    private void showOverdue() {
        StringBuilder sb = new StringBuilder("=== PHIẾU QUÁ HẠN ===\n");
        List<Loan> list = service.getOverdueLoans();
        sb.append("Tổng: ").append(list.size()).append(" phiếu\n\n");
        for (Loan l : list) sb.append(String.format(
                "%s | %s | %s | Trễ %d ngày | Phạt dự kiến: %,.0f đ\n",
                l.getLoanId(), l.getReader().getFullName(), l.getItem().getTitle(),
                l.getDaysLate(), l.getLateFee()));
        setContent("Báo cáo quá hạn", sb.toString());
    }
    private void showTop() {
        StringBuilder sb = new StringBuilder("=== TOP 5 ITEM MƯỢN NHIỀU NHẤT ===\n\n");
        int i=1;
        for (Item it : service.getMostBorrowedItems(5)) {
            long c = service.countLoansOf(it);
            sb.append(String.format("%d. %s - %s (%d lần)\n", i++, it.getTitle(), it.getType(), c));
        }
        setContent("Top mượn nhiều", sb.toString());
    }
    private void showFees() {
        StringBuilder sb = new StringBuilder("=== TỔNG HỢP PHÍ PHẠT ===\n\n");
        sb.append("Đã thu       : ").append(String.format("%,.0f đ", service.getTotalCollectedFees())).append("\n");
        sb.append("Dự kiến còn  : ").append(String.format("%,.0f đ", service.getTotalUnpaidLateFees())).append("\n\n");
        sb.append("--- Chi tiết phiếu đã trả ---\n");
        for (Loan l : service.getAllLoans())
            if (l.getPaidFee() > 0) sb.append(String.format(
                    "%s | %s | %s | %,.0f đ | %s\n",
                    l.getLoanId(), l.getReader().getFullName(), l.getItem().getTitle(),
                    l.getPaidFee(), l.getStatus()));
        setContent("Báo cáo phí phạt", sb.toString());
    }
    private void showHistory(PeriodFilter p) {
        StringBuilder sb = new StringBuilder("=== LỊCH SỬ NHẬP / XOÁ ITEM ===\n");
        sb.append("Kỳ: ").append(p).append("\n\n");
        List<ItemEvent> evs = service.getItemEvents(p);
        sb.append("Tổng: ").append(evs.size()).append(" sự kiện\n\n");
        if (evs.isEmpty()) sb.append("(Không có sự kiện nào)\n");
        else for (ItemEvent e : evs) sb.append(String.format(
                "%s | %-10s | %s | %s | %s\n",
                TimeUtil.fmt(e.getTimestamp()), e.getType().getLabel(),
                e.getItemId(), e.getItemType(), e.getItemTitle()));
        setContent("Lịch sử Item - " + p, sb.toString());
    }
}

// =====================================================================
// 17. MAIN
// =====================================================================
public class LibraryApp {
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}