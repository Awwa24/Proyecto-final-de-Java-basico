import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//parte de shantal suriel
public class TaskManager {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(()-> {
            UIManager.put("swing.bolmetal", Boolean.FALSE);
            new TaskPageFrame(new TaskService()).setVisible(true);
        });
    }

    enum TaskStatus {
        ACTIVA, REALIZADA, DESCARTAR
    }

    static class Task implements Serializable {
        private final String id;
        private String titulo;
        private LocalDate fecha;
        private LocalTime hora;
        private String descripcion;
        private TaskStatus status;

        public Task(String titulo, String descripcion, LocalDate fecha, LocalTime hora) {
            this.id = UUID.randomUUID().toString();
            this.titulo = titulo;
            this.fecha = fecha;
            this.descripcion = descripcion;
            this.hora = hora;
            this.status = TaskStatus.ACTIVA;
        }

        public String getId() { return id; }
        public String getTitulo() { return titulo; }
        public void setTitulo(String titulo) { this.titulo = titulo; }
        public String getDescripcion() { return descripcion; }
        public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
        public LocalDate getFecha() { return fecha; }
        public void setFecha(LocalDate fecha) { this.fecha = fecha; }
        public LocalTime getHora() { return hora; }
        public void setHora(LocalTime hora) { this.hora = hora; }
        public TaskStatus getStatus() { return status; }
        public void setStatus(TaskStatus status) { this.status = status; }
    }


    static class AlertService {
        public static void success(Component parent, String msg) {
            JOptionPane.showMessageDialog(parent, msg, "Listo", JOptionPane.INFORMATION_MESSAGE);
        }

        public static void warning(Component parent, String msg) {
            JOptionPane.showMessageDialog(parent, msg, "Atención", JOptionPane.WARNING_MESSAGE);
        }

        public static void error(Component parent, String msg) {
            JOptionPane.showMessageDialog(parent, msg, "Error", JOptionPane.ERROR_MESSAGE);
        }

        public static boolean confirm(Component parent, String msg) {
            int r = JOptionPane.showConfirmDialog(parent, msg, "Confirmar",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            return r == JOptionPane.YES_OPTION;
        }
    }

    static class TaskStorage {
        private final File file;

        public TaskStorage() {
            String home = System.getProperty("user.home");
            file = new File(home, "Demo");
        }

        public List<Task> load() {
            if (!file.exists()) return new ArrayList<>();
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                Object obj = ois.readObject();
                if (obj instanceof List) {
                    //noinspection unchecked
                    return (List<Task>) obj;
                }
                return new ArrayList<>();
            } catch (Exception ex) {
                return new ArrayList<>();
            }
        }

        public void save(List<Task> tasks) {
            try (ObjectOutputStream listaGuardar = new ObjectOutputStream(new FileOutputStream(file))) {
                listaGuardar.writeObject(tasks);
            } catch (IOException ignored) {
            }
        }
    }

    static class TaskService {
        private final TaskStorage storage = new TaskStorage();
        private final List<Task> tasks = new ArrayList<>();

        public TaskService() {
            tasks.addAll(storage.load());
        }

        public List<Task> getAll() {
            return tasks;
        }

        public void add(Task t) {
            tasks.add(0, t);
            storage.save(tasks);
        }

        public void update(Task t) {
            storage.save(tasks);
        }

        public void delete(Task t) {
            tasks.remove(t);
            storage.save(tasks);
        }

        public void setStatus(Task t, TaskStatus status) {
            t.setStatus(status);
        }
    }

    //Parte de: maicol
    static class TaskPageFrame extends JFrame {

        private final TaskService taskservice;

        /*botones*/
        private final JTextField txtTitulo = new JTextField();
        private final JTextArea txtDesc = new JTextArea(4, 20);
        private final JTextField txtFecha = new JTextField("2026-02-02");
        private final JTextField txtHora = new JTextField("07:00"); //prueba

        private final JButton bntActualizar = new JButton("Actualizar");
        private final JButton bntLimpiar = new JButton("Limpiar");

        /*Creacion de la tabla*/
        private final TaskTableModel tablemodel;
        private final JTable table;

        private Task editingTask = null;

        //Formatos de tiempo/ java.time
        private final DateTimeFormatter F_TIME = DateTimeFormatter.ofPattern("HH:mm");
        private final DateTimeFormatter F_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        public TaskPageFrame(TaskService taskservice) {
            this.taskservice = taskservice;

            this.tablemodel = new TaskTableModel(taskservice.getAll());//getAll metodo de TaskService
            this.table = new JTable(this.tablemodel);
            this.table.setRowHeight(28);
            this.table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

            //caracteristicas de la ventana
            setTitle("Administrador de tareas");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(900,900);
            setLocationRelativeTo(null); //al abrir de abra en el centro

            //contenido al panel para la ventana
            JPanel contenidoPanel = new JPanel(new BorderLayout());
            contenidoPanel.setBorder(new EmptyBorder(12, 12, 12, 12));
            setContentPane(contenidoPanel);

            contenidoPanel.add(buildHeader(), BorderLayout.NORTH);

            JPanel principal = new JPanel(new GridLayout(1, 2, 12, 12));
            contenidoPanel.add(principal, BorderLayout.CENTER);

            JPanel left = buildFormPanel(); //formulario para crear la tarea a la izquierda
            JPanel right = buildTablePanel(); // la tabla con las tareas creadas a la derecha

            //agregar al panel
            principal.add(left);
            principal.add(right);

            right.removeAll();
            right.setLayout(new BorderLayout(10, 10));
            right.add(buildTableHeader(), BorderLayout.NORTH);
            right.add(new JScrollPane(table), BorderLayout.CENTER);
            right.add(buildActionsPanel(), BorderLayout.SOUTH);

            //escuchar las acciones de cada boton a su metodo
            bntActualizar.addActionListener(e -> onSave());
            bntLimpiar.addActionListener(e -> resetForm());

            table.getSelectionModel().addListSelectionListener(this::onRowSelected);

            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    super.mouseClicked(e);
                    if (e.getClickCount() == 2) {
                        Task t = getSelectedTask();
                        if (t != null) loadToForm(t);
                    }
                }
            });

            resetForm();
            refreshTable();
        }

        //Encabezado del panel
        private JPanel buildHeader() {
            JPanel p = new JPanel(new BorderLayout());
            p.setBorder(new EmptyBorder(0, 0, 10, 0));

            JLabel title = new JLabel("Administrador de tareas");
            title.setFont(new Font("Arial", Font.BOLD, 22));

            JLabel subt = new JLabel("Formulario");
            subt.setForeground(new Color(90, 90, 90));

            JPanel left = new JPanel(new GridLayout(2, 1));
            left.setOpaque(false);
            left.add(title);
            left.add(subt);

            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            right.setOpaque(false);

            p.add(left, BorderLayout.WEST);
            p.add(right, BorderLayout.EAST);
            return p;
        }

        //Las tablas del form
        private JPanel buildFormPanel() {
            JPanel card = new JPanel(new BorderLayout(10, 10));
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(220, 220, 220)),
                    new EmptyBorder(12, 12, 12, 12)
            ));

            JLabel titleform = new JLabel("Crear / editar tus tareas");
            titleform.setFont(new Font("Arial", Font.BOLD, 16));
            card.add(titleform, BorderLayout.NORTH);

            JPanel form = new JPanel(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(6, 6, 6, 6);
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;

            c.gridx = 0;
            c.gridy = 0;
            form.add(new JLabel("Titulo"), c);
            c.gridx = 1;
            txtTitulo.setFont(new Font("Arial", Font.PLAIN, 14));
            form.add(txtTitulo, c);

            c.gridx = 0;
            c.gridy = 1;
            form.add(new JLabel("Fecha"), c);
            c.gridx = 1;
            txtFecha.setFont(new Font("Arial", Font.PLAIN, 14));
            form.add(txtFecha, c);

            c.gridx = 0;
            c.gridy = 2;
            form.add(new JLabel("Hora"), c);
            c.gridx = 1;
            txtHora.setFont(new Font("Arial", Font.PLAIN, 14));
            form.add(txtHora, c);

            c.gridx = 0;
            c.gridy = 3;
            c.anchor = GridBagConstraints.NORTHWEST; //ancho de panel y dirigido
            form.add(new JLabel("Descripcion"), c);

            c.gridx = 1;
            c.fill = GridBagConstraints.BOTH;
            c.weighty = 1;
            txtDesc.setLineWrap(true);
            txtDesc.setWrapStyleWord(true);
            txtDesc.setFont(new Font("Arial", Font.PLAIN, 14));
            form.add(new JScrollPane(txtDesc), c);

            card.add(form, BorderLayout.CENTER);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            bntActualizar.setFont(new Font("Arial", Font.BOLD, 13));
            bntLimpiar.setFont(new Font("Arial", Font.BOLD, 13));
            actions.add(bntActualizar);
            actions.add(bntLimpiar);

            card.add(actions, BorderLayout.SOUTH);
            return card;

        }

        private JPanel buildTablePanel() {
            JPanel card = new JPanel(new BorderLayout(10, 10));
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(220, 220, 220)),
                    new EmptyBorder(12, 12, 12, 12)
            ));
            return card;

        }

        private JPanel buildTableHeader() {
            JPanel p = new JPanel(new BorderLayout());
            JLabel h = new JLabel("Tus tareas");
            h.setFont(new Font("Arial", Font.BOLD, 16));

            JPanel left = new JPanel(new GridLayout(2, 1));
            left.setOpaque(false);
            left.add(h);

            p.add(left, BorderLayout.WEST);
            return p;
        }

        private JPanel buildActionsPanel() {
            JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));

            JButton bntEditar = new JButton("Editar");
            JButton bntEliminar = new JButton("Eliminar");
            JButton bntRealizada = new JButton("Realizada");
            JButton bntDescartar = new JButton("Descartar");
            JButton bntReabrir = new JButton("Reabrir");

            bntEditar.addActionListener(e -> {
                Task t = getSelectedTask();
                if (t == null) return;
                loadToForm(t);
            });

            bntEliminar.addActionListener(e -> {
                Task t = getSelectedTask();
                if (t == null) return;
                taskservice.delete(t); //metodo de taskService
                boolean ok = AlertService.confirm(this, "Seguro de eliminar?");
                if(!ok) return;

                resetForm();
                refreshTable();

            });

            bntRealizada.addActionListener(e -> {
                Task t = getSelectedTask();
                if (t == null) return;
                if (t.getStatus() == TaskStatus.REALIZADA){
                    AlertService.warning(this, "Esta tarea ya estara realizada");
                    return;
                }
                boolean ok = AlertService.confirm(this,"Marcar como realizada?");
                if(!ok) return;
                taskservice.setStatus(t, TaskStatus.REALIZADA);
                AlertService.success(this, "Marcada como realizada");
                refreshTable();
            });

            bntReabrir.addActionListener(e -> {
                Task t = getSelectedTask();
                if (t == null) return;
                if (t.getStatus() == TaskStatus.ACTIVA){
                    AlertService.warning(this, "Ya esta Activa");
                    return;
                }
                boolean ok = AlertService.confirm(this, "desea reabrir?");
                if (!ok) return;
                taskservice.setStatus(t, TaskStatus.ACTIVA);
                refreshTable();
            });

            bntDescartar.addActionListener(e -> {
                Task t = getSelectedTask();
                if (t == null) return;
                taskservice.setStatus(t, TaskStatus.DESCARTAR);

                refreshTable();
            });

            //agregar los botones
            p.add(bntReabrir);
            p.add(bntDescartar);
            p.add(bntEditar);
            p.add(bntRealizada);
            p.add(bntEliminar);

            return p;
        }

        //seleccioner una fila
        private void onRowSelected(ListSelectionEvent e) {
            if (e.getValueIsAdjusting())
                return;
            getSelectedTask();
        }

        private void onSave() {
            String titulo = txtTitulo.getText().trim();
            String desc = txtDesc.getText().trim();
            String sfecha = txtFecha.getText().trim();
            String shora = txtHora.getText().trim();

            if (titulo.isEmpty()) {
                AlertService.warning(this, "Este campo es requerido");
                txtTitulo.requestFocus();
                return;
            }

            if (desc.isEmpty()) {
                AlertService.warning(this, "Este campo es requerido");
                txtDesc.requestFocus();
                return;
            }

            LocalDate fecha;
            LocalTime hora;

            try {
                fecha = LocalDate.parse(sfecha, F_DATE);
            } catch (Exception ex) {
                AlertService.error(this, "Fecha inválida. Formato requerido: yyyy-MM-dd");
                txtFecha.requestFocus();
                return;
            }

            try {
                hora = LocalTime.parse(shora, F_TIME);
            } catch (Exception ex) {
                AlertService.error(this, "Hora invalida");
                txtHora.requestFocus();
                return;
            }

            if (editingTask == null) {
                Task t = new Task(titulo, desc, fecha, hora);
                taskservice.add(t);
            } else {
                boolean ok = AlertService.confirm(this, "Se edito");
                if (!ok) return;

                editingTask.setTitulo(titulo);
                editingTask.setDescripcion(desc);
                editingTask.setFecha(fecha);
                editingTask.setHora(hora);
                taskservice.update(editingTask);
            }

            resetForm();
            refreshTable();
        }

        private void loadToForm(Task t) {
            editingTask = t;
            txtTitulo.setText(t.getTitulo());
            txtDesc.setText(t.getDescripcion());
            txtFecha.setText(t.getFecha().format(F_DATE));
            txtHora.setText(t.getHora().format(F_TIME));
            bntActualizar.setText("Actualizar");
        }

        private void resetForm() {
            editingTask = null;
            txtTitulo.setText("");
            txtDesc.setText("");
            txtFecha.setText(LocalDate.now().format(F_DATE));
            txtHora.setText(LocalTime.now().withSecond(0).withNano(0).format(F_TIME));
            bntActualizar.setText("Actualizar");
            txtTitulo.requestFocus();
        }

        //seleccionar una tarea ya seleccionada
        private Task getSelectedTask() {
            int row = table.getSelectedRow();
            if (row < 0) {
                return null;
            }

            int modelRow = table.convertRowIndexToModel(row);
            return tablemodel.getTaskAt(modelRow);
        }

        private void refreshTable() {
            tablemodel.fireTableDataChanged();
        }
    }
        static class  TaskTableModel extends AbstractTableModel{
            private final List<Task> tasks;

            //las propiedades/campos de las columnas
            private final String[] cols = {
                    "Titulo", "Descripcion", "Fecha", "Hora", "Estado"
            };

            private final DateTimeFormatter F_DATE = DateTimeFormatter.ofPattern("yyy-MM-dd");
            private final DateTimeFormatter F_TIME = DateTimeFormatter.ofPattern("HH:mm");

            public TaskTableModel(List<Task> tasks){
                this.tasks = tasks;
            }

            public  Task getTaskAt(int row){
                return tasks.get(row);
            }

            @Override public int getRowCount() { return tasks.size(); }
            @Override public int getColumnCount() { return cols.length; }
            @Override public String getColumnName(int column) { return cols[column]; }

            @Override public Object getValueAt(int rowIndex, int columnIndex) {
                Task t = tasks.get(rowIndex);
                return switch (columnIndex) {
                    case 0 -> t.getTitulo();
                    case 1 -> ellipsis(t.getDescripcion(), 60);
                    case 2 -> t.getFecha().format(F_DATE);
                    case 3 -> t.getHora().format(F_TIME);
                    case 4 -> t.getStatus().name();
                    default -> "";
                };
            }

            private String ellipsis(String s, int max) {
                if (s == null) return "";
                if (s.length() <= max) return s;
                return s.substring(0, max - 3) + "...";
            }

            @Override public boolean isCellEditable(
                    int rowIndex,
                    int columnIndex)
            { return false; }
        }
}
